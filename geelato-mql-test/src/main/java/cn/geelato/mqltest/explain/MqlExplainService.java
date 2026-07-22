package cn.geelato.mqltest.explain;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.mql.MetaQLManager;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.parser.JsonTextQueryParser;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.core.sql.provider.MetaQuerySqlProvider;
import cn.geelato.mqltest.dto.MqlExplainResult;
import cn.geelato.mqltest.dto.MqlValidateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MQL explain 服务。
 * <p>
 * 复用 {@link JsonTextQueryParser} + {@link MetaQuerySqlProvider} 的核心链路，
 * 在 dry-run 模式下返回生成的 SQL/params/types，不执行。
 * <p>
 * 支持查询（query）、保存（save：自动判 Insert/Update）、删除（delete）三类操作的 dry-run。
 */
@Slf4j
@Service
public class MqlExplainService {

    private final JsonTextQueryParser queryParser = new JsonTextQueryParser();
    private final MetaQLManager gqlManager = MetaQLManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();

    /**
     * explain（dry-run）：按指定操作类型解析 MQL JSON 并生成 SQL，不执行。
     *
     * @param mqlJson MQL JSON 文本
     * @param op      操作类型：query（默认）/ save / batchSave / delete
     * @return explain 结果（含 SQL/params/types/countSql/AST）
     */
    public MqlExplainResult explain(String mqlJson, String op) {
        try {
            String operation = op == null ? "query" : op.toLowerCase();
            switch (operation) {
                case "save":
                case "save-update":
                case "save-insert":
                    return explainSave(mqlJson);
                case "batchsave":
                    return explainBatchSave(mqlJson);
                case "delete":
                case "delete-by-id":
                case "delete-by-where":
                    return explainDelete(mqlJson);
                case "query":
                default:
                    QueryCommand command = queryParser.parse(mqlJson);
                    return doExplain(command);
            }
        } catch (Exception e) {
            log.warn("MQL explain 解析失败: {}", e.getMessage());
            return MqlExplainResult.fail(e.getMessage());
        }
    }

    /** 兼容旧调用（默认 query） */
    public MqlExplainResult explain(String mqlJson) {
        return explain(mqlJson, "query");
    }

    private MqlExplainResult explainSave(String mqlJson) {
        SaveCommand command = gqlManager.generateSaveSql(mqlJson, new SessionCtx());
        BoundSql boundSql = sqlManager.generateSaveSql(command);
        MqlExplainResult r = baseResult(command.getEntityName(), boundSql);
        r.setAst(buildAstMap("save/" + command.getCommandType(), command.getEntityName()));
        return r;
    }

    private MqlExplainResult explainBatchSave(String mqlJson) {
        List<SaveCommand> commands = gqlManager.generateBatchSaveSql(mqlJson, new SessionCtx());
        if (commands.isEmpty()) {
            return MqlExplainResult.fail("批量保存命令为空");
        }
        // 批量场景展示第一条 SQL，AST 标注数量
        List<BoundSql> boundSqls = sqlManager.generateBatchSaveSql(commands);
        BoundSql first = boundSqls.get(0);
        MqlExplainResult r = baseResult(commands.get(0).getEntityName(), first);
        Map<String, Object> ast = buildAstMap("batchSave", commands.get(0).getEntityName());
        ast.put("batchCount", commands.size());
        r.setAst(ast);
        return r;
    }

    private MqlExplainResult explainDelete(String mqlJson) {
        DeleteCommand command = gqlManager.generateDeleteSql(mqlJson, new SessionCtx());
        BoundSql boundSql = sqlManager.generateDeleteSql(command);
        MqlExplainResult r = baseResult(command.getEntityName(), boundSql);
        r.setAst(buildAstMap("delete", command.getEntityName()));
        return r;
    }

    private MqlExplainResult baseResult(String entityName, BoundSql boundSql) {
        MqlExplainResult r = new MqlExplainResult();
        r.setEntityName(entityName);
        r.setSql(boundSql.getSql());
        r.setParams(boundSql.getParams());
        r.setTypes(boundSql.getTypes());
        return r;
    }

    private Map<String, Object> buildAstMap(String op, String entityName) {
        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("op", op);
        ast.put("entityName", entityName);
        return ast;
    }

    /**
     * 对已解析的 QueryCommand 执行 explain（内部使用，支持预解析场景）。
     */
    public MqlExplainResult doExplain(QueryCommand command) {
        try {
            MetaQuerySqlProvider provider = new MetaQuerySqlProvider();
            BoundPageSql bps = new BoundPageSql();
            bps.setBoundSql(provider.generate(command));
            bps.setCountSql(provider.buildCountSql(command));

            MqlExplainResult result = new MqlExplainResult();
            result.setEntityName(command.getEntityName());
            result.setSql(bps.getBoundSql().getSql());
            result.setParams(bps.getBoundSql().getParams());
            result.setTypes(bps.getBoundSql().getTypes());
            result.setCountSql(bps.getCountSql());
            result.setPagingQuery(command.isPagingQuery());
            result.setFields(command.getFields());
            result.setOrderBy(command.getOrderBy());
            result.setGroupBy(command.getGroupBy());
            result.setPageNum(command.getPageNum());
            result.setPageSize(command.getPageSize());
            result.setAst(buildAstSnapshot(command));
            return result;
        } catch (Exception e) {
            log.warn("MQL explain SQL 生成失败: {}", e.getMessage());
            return MqlExplainResult.fail(e.getMessage());
        }
    }

    /**
     * 仅校验 MQL JSON 合法性。
     */
    public MqlValidateResult validate(String mqlJson) {
        MqlValidateResult result = new MqlValidateResult();
        result.setValid(true);
        try {
            QueryCommand command = queryParser.parse(mqlJson);
            result.setEntityName(command.getEntityName());
        } catch (Exception e) {
            result.addError(e.getMessage());
        }
        return result;
    }

    /**
     * 获取可用实体名列表。
     */
    public List<String> listEntities() {
        return MetaManager.singleInstance().getAllEntityNames().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取实体元数据（字段/列名/类型/JSON/外键）。
     */
    public Map<String, Object> getEntitySchema(String entityName) {
        EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
        if (em == null) {
            return null;
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("entityName", em.getEntityName());
        schema.put("tableName", em.getTableName());
        if (em.getTableMeta() != null) {
            schema.put("dbType", em.getTableMeta().getDbType());
        }

        // 字段列表
        List<Map<String, Object>> fields = new java.util.ArrayList<>();
        if (em.getFieldMetas() != null) {
            for (FieldMeta fm : em.getFieldMetas()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("fieldName", fm.getFieldName());
                field.put("columnName", fm.getColumnName());
                if (fm.getColumnMeta() != null) {
                    field.put("dataType", fm.getColumnMeta().getDataType());
                    field.put("isJson", "JSON".equalsIgnoreCase(fm.getColumnMeta().getDataType()));
                }
                fields.add(field);
            }
        }
        schema.put("fields", fields);

        // 外键关系
        List<Map<String, Object>> foreigns = new java.util.ArrayList<>();
        if (em.getTableForeigns() != null) {
            for (TableForeign tf : em.getTableForeigns()) {
                Map<String, Object> fk = new LinkedHashMap<>();
                fk.put("mainTableCol", tf.getMainTableCol());
                fk.put("foreignTable", tf.getForeignTable());
                fk.put("foreignTableCol", tf.getForeignTableCol());
                fk.put("enableStatus", tf.getEnableStatus());
                foreigns.add(fk);
            }
        }
        schema.put("foreigns", foreigns);

        return schema;
    }

    /**
     * 构建 QueryCommand 的 AST 快照（序列化关键字段，供前端展示）。
     */
    private Map<String, Object> buildAstSnapshot(QueryCommand command) {
        Map<String, Object> ast = new LinkedHashMap<>();
        ast.put("entityName", command.getEntityName());
        ast.put("fields", command.getFields());
        ast.put("orderBy", command.getOrderBy());
        ast.put("groupBy", command.getGroupBy());
        ast.put("pageNum", command.getPageNum());
        ast.put("pageSize", command.getPageSize());
        ast.put("pagingQuery", command.isPagingQuery());
        ast.put("foreignFields", command.getForeignFields());
        ast.put("alias", command.getAlias());
        if (command.getWhere() != null) {
            ast.put("whereLogic", command.getWhere().getLogic() != null ? command.getWhere().getLogic().name() : null);
            ast.put("whereFilterCount", command.getWhere().getFilters() != null ? command.getWhere().getFilters().size() : 0);
            ast.put("whereChildGroupCount", command.getWhere().getChildFilterGroup() != null ? command.getWhere().getChildFilterGroup().size() : 0);
        }
        return ast;
    }
}
