package cn.geelato.script.kit.builtin;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.core.TraceEntry;
import cn.geelato.script.kit.KitContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL kit（$gl.sql）：命名参数 SQL 桥（:name 参数），数据源经 dataSourceSupplier 由宿主装配。
 * 读方法受 row-limit/query-timeout-ms 收口并上报行数；写方法（update/execute）受
 * dao.write-enabled 总开关门控，dry-run 语义为事务内执行后回滚（ScriptExecution.dryRun 可执行级覆盖）。
 */
@Slf4j
@GraalService(name = "sql", built = "true", descrption = "SQL 桥（query/queryOne/queryValue/update/execute；命名参数、行数与超时收口、写门控）")
public class SqlKit {

    private final KitContext ctx;

    public SqlKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.sql.query('select id, name from t where status = :status', {status: 'OPEN'})",
            description = "查询返回行列表（Map 字段名→值）；受 row-limit 截断")
    public Object query(String sql, Map<String, Object> params) {
        return cn.geelato.script.lang.GuestValues.toGuest(queryInternal(sql, params));
    }

    @GraalFunction(example = "$gl.sql.queryOne('select * from t where id = :id', {id: '1'})",
            description = "查询单行（无结果返回 null）")
    public Object queryOne(String sql, Map<String, Object> params) {
        List<Map<String, Object>> rows = queryInternal(sql, params);
        return rows.isEmpty() ? null : cn.geelato.script.lang.GuestValues.toGuest(rows.get(0));
    }

    @GraalFunction(example = "$gl.sql.queryValue('select count(*) from t', {})", description = "查询单值（无结果返回 null）")
    public Object queryValue(String sql, Map<String, Object> params) {
        List<Map<String, Object>> rows = queryInternal(sql, params);
        if (rows.isEmpty()) {
            return null;
        }
        return cn.geelato.script.lang.GuestValues.toGuest(rows.get(0).values().iterator().next());
    }

    @GraalFunction(example = "$gl.sql.update('update t set status = :s where id = :id', {s: 'CLOSED', id: '1'})",
            description = "写语句（INSERT/UPDATE/DELETE）；受 write-enabled 门控，dry-run 时事务内执行后回滚")
    public Object update(String sql, Map<String, Object> params) {
        requireWritable();
        long start = System.currentTimeMillis();
        Integer affected = executeWrite(sql, orEmpty(params));
        ctx.record(TraceEntry.of("sql", "update", sql, affected == null ? 0 : affected,
                System.currentTimeMillis() - start));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("affected", affected == null ? 0 : affected);
        out.put("dryRun", ctx.isDaoDryRun());
        return cn.geelato.script.lang.GuestValues.toGuest(out);
    }

    @GraalFunction(example = "$gl.sql.execute('create index idx_t on t(name)')",
            description = "执行 DDL/无参语句；门控同 update")
    public Object execute(String sql) {
        return update(sql, Map.of());
    }

    // ==================== 内部 ====================

    private List<Map<String, Object>> queryInternal(String sql, Map<String, Object> params) {
        dataSource();
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc().queryForList(sql, orEmpty(params));
        if (rows.size() > ctx.getSqlRowLimit()) {
            rows = rows.subList(0, ctx.getSqlRowLimit());
        }
        ctx.addRows(rows.size());
        ctx.record(TraceEntry.of("sql", "query", sql, rows.size(), System.currentTimeMillis() - start));
        return rows;
    }

    private NamedParameterJdbcTemplate jdbc() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        jdbcTemplate.setMaxRows(ctx.getSqlRowLimit());
        jdbcTemplate.setQueryTimeout((int) Math.ceil(ctx.getSqlQueryTimeoutMs() / 1000.0));
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private Integer executeWrite(String sql, Map<String, Object> params) {
        DataSource dataSource = dataSource();
        if (!ctx.isDaoDryRun()) {
            return jdbc().update(sql, params);
        }
        DataSourceTransactionManager tm = new DataSourceTransactionManager(dataSource);
        TransactionStatus status = tm.getTransaction(new DefaultTransactionDefinition());
        Integer affected;
        try {
            affected = jdbc().update(sql, params);
        } finally {
            tm.rollback(status);
        }
        log.info("KIT-DRYRB exec={} sql dry-run 已回滚 affected={} :: {}", ctx.getExecId(), affected, TraceEntry.summarize(sql));
        return affected;
    }

    private DataSource dataSource() {
        if (ctx.getDataSourceSupplier() == null) {
            throw new IllegalArgumentException("SQL 数据源未配置（ScriptEngineBuilder.dataSourceSupplier），无法调用 $gl.sql");
        }
        DataSource dataSource = ctx.getDataSourceSupplier().get();
        if (dataSource == null) {
            throw new IllegalArgumentException("SQL 数据源解析为空（dataSourceSupplier 返回 null），无法调用 $gl.sql");
        }
        return dataSource;
    }

    private void requireWritable() {
        dataSource();
        if (!ctx.isDaoWriteEnabled()) {
            throw new IllegalArgumentException("SQL 写通道未启用（geelato.script.engine.dao.write-enabled），拒绝执行写语句");
        }
    }

    private static Map<String, Object> orEmpty(Map<String, Object> params) {
        return params == null ? Map.of() : params;
    }
}
