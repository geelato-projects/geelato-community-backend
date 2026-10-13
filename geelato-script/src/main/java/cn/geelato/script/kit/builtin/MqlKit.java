package cn.geelato.script.kit.builtin;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.mql.MetaQLManager;
import cn.geelato.core.mql.MqlQueryProcessor;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.script.core.TraceEntry;
import cn.geelato.script.kit.KitContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MQL kit（$gl.mql）：元数据查询桥——方法面对齐平台 MetaController（list/multiList/save/batchSave/
 * multiSave/delete/deleteByGql），MQL JSON 经平台统一处理器解析，按实体元数据 connectId 经
 * ormWrapper 重建路由与身份。写方法受 dao.write-enabled 门控，dry-run 语义为事务内执行后回滚。
 */
@Slf4j
@GraalService(name = "mql", built = "true", descrption = "MQL 元数据查询（list/multiList/save/batchSave/multiSave/delete/deleteByGql/explain）")
public class MqlKit {

    private final KitContext ctx;

    public MqlKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.mql.list('{\"platform_user\": {\"@fs\": \"id,userName\", \"@p\": \"1,20\"}}')",
            description = "单实体查询（对齐 /meta/list），返回 {data, total, page, size}")
    public Object list(String mqlJson) {
        requireDao();
        long start = System.currentTimeMillis();
        MqlQueryProcessor.ProcessedQuery processed = MqlQueryProcessor.getInstance().process(mqlJson);
        Map<String, Object> out = runQuery(processed.getCommand(), processed.getBoundPageSql());
        ctx.addRows(((List<?>) out.get("data")).size());
        ctx.record(TraceEntry.of("mql", "list", processed.getCommand().getEntityName(),
                ((List<?>) out.get("data")).size(), System.currentTimeMillis() - start));
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.multiList('{\"a\": {\"@fs\": \"id\"}, \"b\": {\"@fs\": \"id\"}}')",
            description = "多实体查询（对齐 /meta/multiList），返回 {data: {实体名: {data, total, page, size}}}")
    public Object multiList(String mqlJson) {
        requireDao();
        long start = System.currentTimeMillis();
        List<QueryCommand> commandList = MetaQLManager.singleInstance().generateMultiQuerySql(mqlJson);
        Map<String, Object> dataMap = new LinkedHashMap<>();
        for (QueryCommand command : commandList) {
            MqlQueryProcessor.getInstance().applyViewTemplateParams(command, null);
            MqlQueryProcessor.getInstance().processQueryCommandFunctions(command);
            BoundPageSql boundPageSql = SqlManager.singleInstance().generatePageQuerySql(command);
            dataMap.put(command.getEntityName(), runQuery(command, boundPageSql));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", dataMap);
        ctx.record(TraceEntry.of("mql", "multiList", dataMap.keySet().toString(), 0,
                System.currentTimeMillis() - start));
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.save('{\"platform_user\": {\"userName\": \"a\"}}')",
            description = "保存（对齐 /meta/save/{biz}），返回 {pk, dryRun}；受 write-enabled 门控")
    public Object save(String mqlJson) {
        requireDao();
        requireWritable();
        long start = System.currentTimeMillis();
        String pk = withWriteGuard(sessionCtx -> {
            SaveCommand command = MetaQLManager.singleInstance().generateSaveSql(mqlJson, sessionCtx);
            BoundSql boundSql = SqlManager.singleInstance().generateSaveSql(command);
            return executeWrite(command.getEntityName(), () -> dao().save(boundSql), null);
        });
        ctx.record(TraceEntry.of("mql", "save", entityOf(mqlJson), 1, System.currentTimeMillis() - start));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pk", pk);
        out.put("dryRun", ctx.isDaoDryRun());
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.batchSave('[{\"platform_user\": {\"userName\": \"a\"}}]')",
            description = "批量保存（对齐 /meta/batchSave），返回 {pks, dryRun}；受 write-enabled 门控")
    public Object batchSave(String mqlJson) {
        requireDao();
        requireWritable();
        long start = System.currentTimeMillis();
        List<String> pks = withWriteGuard(sessionCtx -> {
            List<SaveCommand> commandList = MetaQLManager.singleInstance().generateBatchSaveSql(mqlJson, sessionCtx);
            List<String> outPks = new ArrayList<>();
            for (SaveCommand command : commandList) {
                BoundSql boundSql = SqlManager.singleInstance().generateSaveSql(command);
                outPks.add(executeWrite(command.getEntityName(), () -> dao().save(boundSql), null));
            }
            return outPks;
        });
        ctx.record(TraceEntry.of("mql", "batchSave", entityOf(mqlJson), pks.size(), System.currentTimeMillis() - start));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pks", pks);
        out.put("dryRun", ctx.isDaoDryRun());
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.multiSave('{\"a\": {\"name\": \"x\"}, \"b\": {\"code\": \"y\"}}')",
            description = "多实体保存（对齐 /meta/multiSave），返回 {count, dryRun}；受 write-enabled 门控")
    public Object multiSave(String mqlJson) {
        requireDao();
        requireWritable();
        long start = System.currentTimeMillis();
        Object raw = withWriteGuard(sessionCtx -> {
            List<SaveCommand> commandList = MetaQLManager.singleInstance().generateMultiSaveSql(mqlJson, sessionCtx);
            List<BoundSql> boundSqlList = SqlManager.singleInstance().generateBatchSaveSql(commandList);
            String firstEntity = commandList.isEmpty() ? null : commandList.get(0).getEntityName();
            return executeWrite(firstEntity, () -> dao().multiSave(boundSqlList), boundSqlList.size());
        });
        int count = raw instanceof Number n ? n.intValue() : 0;
        ctx.record(TraceEntry.of("mql", "multiSave", entityOf(mqlJson), count,
                System.currentTimeMillis() - start));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("dryRun", ctx.isDaoDryRun());
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.delete('platform_user', 'id1,id2')",
            description = "按 id 删除（对齐 /meta/delete/{biz}/{id}，id 支持逗号分隔多值），返回 {affected, dryRun}")
    public Object delete(String entityName, String id) {
        requireDao();
        requireWritable();
        if (entityName == null || entityName.isBlank() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("delete 需要 entityName 与 id（id 支持逗号分隔多值）");
        }
        long start = System.currentTimeMillis();
        FilterGroup filterGroup = id.contains(",")
                ? new FilterGroup().addFilter("id", FilterGroup.Operator.in, id)
                : new FilterGroup().addFilter("id", id);
        BoundSql boundSql = SqlManager.singleInstance().generateDeleteSql(entityName.trim(), filterGroup);
        Integer affected = executeWrite(entityName.trim(), () -> dao().delete(boundSql), 0);
        ctx.record(TraceEntry.of("mql", "delete", entityName, affected == null ? 0 : affected,
                System.currentTimeMillis() - start));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("affected", affected == null ? 0 : affected);
        out.put("dryRun", ctx.isDaoDryRun());
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.deleteByGql('{\"platform_user\": {\"status|eq\": \"0\"}}')",
            description = "按条件删除（对齐 /meta/delete2/{biz}），返回 {affected, dryRun}；受 write-enabled 门控")
    public Object deleteByGql(String mqlJson) {
        requireDao();
        requireWritable();
        long start = System.currentTimeMillis();
        Integer affected = withWriteGuard(sessionCtx -> {
            DeleteCommand command = MetaQLManager.singleInstance().generateDeleteSql(mqlJson, sessionCtx);
            BoundSql boundSql = SqlManager.singleInstance().generateDeleteSql(command);
            return executeWrite(command.getEntityName(), () -> dao().delete(boundSql), 0);
        });
        ctx.record(TraceEntry.of("mql", "deleteByGql", entityOf(mqlJson), affected == null ? 0 : affected,
                System.currentTimeMillis() - start));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("affected", affected == null ? 0 : affected);
        out.put("dryRun", ctx.isDaoDryRun());
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.mql.explain('{\"platform_user\": {\"status|eq\": \"1\"}}', 'list')",
            description = "解释不执行（op=list/save/delete），返回 {sql, params, countSql}")
    public Object explain(String mqlJson, String op) {
        requireDao();
        String operation = op == null || op.isBlank() ? "list" : op.trim().toLowerCase();
        BoundSql boundSql;
        String countSql = null;
        switch (operation) {
            case "save" -> {
                SaveCommand command = MetaQLManager.singleInstance().generateSaveSql(mqlJson, buildSessionCtx());
                boundSql = SqlManager.singleInstance().generateSaveSql(command);
            }
            case "delete" -> {
                DeleteCommand command = MetaQLManager.singleInstance().generateDeleteSql(mqlJson, buildSessionCtx());
                boundSql = SqlManager.singleInstance().generateDeleteSql(command);
            }
            case "list", "query" -> {
                MqlQueryProcessor.ProcessedQuery processed = MqlQueryProcessor.getInstance().process(mqlJson);
                boundSql = processed.getBoundPageSql().getBoundSql();
                countSql = processed.getBoundPageSql().getCountSql();
            }
            default -> throw new IllegalArgumentException("不支持的 explain 操作: " + operation + "（可用: list/save/delete）");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sql", boundSql.getSql());
        out.put("params", boundSql.getParams());
        out.put("countSql", countSql);
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    // ==================== 内部 ====================

    /** 单实体查询执行：实体路由 wrapper + 分页结果（对齐 RuleService.createPageResult 的核心键）。 */
    private Map<String, Object> runQuery(QueryCommand command, BoundPageSql boundPageSql) {
        String connectId = entityConnectId(command.getEntityName());
        return ctx.getOrmWrapper().run(connectId, ctx.getIdentity(), () -> {
            Dao dao = dao();
            List<Map<String, Object>> data = dao.queryForMapList(boundPageSql);
            Long total = dao.queryTotal(boundPageSql);
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("data", data);
            page.put("total", total == null ? 0L : total);
            page.put("page", command.getPageNum());
            page.put("size", command.getPageSize());
            return page;
        });
    }

    @FunctionalInterface
    private interface SessionCtxAction<T> {
        T apply(SessionCtx sessionCtx);
    }

    /**
     * 写命令生成统一包裹：wrapper 内构造 SessionCtx（吃到宿主 ThreadLocal 重建 + identity 兜底）
     * ——命令生成须在 SessionCtx 语境下完成（模板变量）。
     */
    private <T> T withWriteGuard(SessionCtxAction<T> action) {
        return ctx.getOrmWrapper().run(null, ctx.getIdentity(), () -> action.apply(buildSessionCtx()));
    }

    /**
     * 写执行落地：实体 connectId 路由 + dry-run 时事务内执行后回滚；null 结果以 dryRunFallback 兜底。
     */
    private <T> T executeWrite(String entityName, Supplier<T> action, T dryRunFallback) {
        String connectId = entityConnectId(entityName);
        Supplier<T> routed = (connectId == null || connectId.isBlank())
                ? action
                : () -> ctx.getOrmWrapper().run(connectId, ctx.getIdentity(), action);
        if (!ctx.isDaoDryRun()) {
            return routed.get();
        }
        DataSourceTransactionManager tm = new DataSourceTransactionManager(
                dao().getJdbcTemplate().getDataSource());
        TransactionStatus status = tm.getTransaction(new DefaultTransactionDefinition());
        T result;
        try {
            result = routed.get();
            result = result == null ? dryRunFallback : result;
        } finally {
            tm.rollback(status);
        }
        log.info("KIT-DRYRB exec={} mql[{}] dry-run 已回滚 result={}", ctx.getExecId(), entityName, result);
        return result;
    }

    private Dao dao() {
        Dao dao = ctx.getDaoSupplier().get();
        if (dao == null) {
            throw new IllegalArgumentException("ORM Dao 解析为空（daoSupplier 返回 null），无法调用 $gl.mql");
        }
        return dao;
    }

    /** SessionCtx：ThreadLocal（wrapper 重建后可用）+ identity 快照兜底填充。 */
    private SessionCtx buildSessionCtx() {
        SessionCtx sessionCtx = new SessionCtx();
        cn.geelato.security.User user = ctx.getIdentity() == null ? null : ctx.getIdentity().getUser();
        if (user != null) {
            sessionCtx.putIfAbsent("userId", user.getUserId());
            sessionCtx.putIfAbsent("userName", user.getUserName());
            sessionCtx.putIfAbsent("orgId", user.getOrgId());
        }
        if (ctx.getIdentity() != null && ctx.getIdentity().getTenant() != null) {
            sessionCtx.putIfAbsent("tenantCode", ctx.getIdentity().getTenant().getCode());
        }
        return sessionCtx;
    }

    private void requireDao() {
        if (ctx.getDaoSupplier() == null) {
            throw new IllegalArgumentException("ORM Dao 未配置（ScriptEngineBuilder.daoSupplier），无法调用 $gl.mql");
        }
    }

    private void requireWritable() {
        if (!ctx.isDaoWriteEnabled()) {
            throw new IllegalArgumentException("MQL 写通道未启用（geelato.script.engine.dao.write-enabled），拒绝执行写操作");
        }
    }

    /** 实体元数据 connectId（空=默认库）；实体未注册返回 null。 */
    private static String entityConnectId(String entityName) {
        try {
            return MetaManager.singleInstance().getByEntityName(entityName).getTableMeta().getConnectId();
        } catch (Exception e) {
            return null;
        }
    }

    /** MQL JSON 根键（实体名）提取，仅作 trace 摘要。 */
    private static String entityOf(String mqlJson) {
        try {
            Map<String, Object> root = com.alibaba.fastjson2.JSON.parseObject(mqlJson, Map.class);
            return root == null || root.isEmpty() ? "?" : String.valueOf(root.keySet().iterator().next());
        } catch (Exception e) {
            return "?";
        }
    }
}
