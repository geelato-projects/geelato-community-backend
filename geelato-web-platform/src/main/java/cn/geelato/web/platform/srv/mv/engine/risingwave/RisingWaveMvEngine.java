package cn.geelato.web.platform.srv.mv.engine.risingwave;

import cn.geelato.datasource.PlatformDataSources;
import cn.geelato.web.platform.srv.mv.engine.MaterializedViewEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RisingWave 物化视图引擎适配器（PG 协议）。
 * 摄取：两段式（CREATE SOURCE 连接定义 + CREATE TABLE ... FROM source TABLE (*)）。
 * 引擎目录：rw_relations / rw_materialized_views，回退 information_schema。
 */
@Component
@Slf4j
public class RisingWaveMvEngine implements MaterializedViewEngine {

    private static final String ENGINE_TYPE = "risingwave";
    private static final int AUTO_TABLE_WAIT_MILLIS = 5000;
    private static final int AUTO_TABLE_POLL_INTERVAL_MILLIS = 500;
    /** 回填期摄取限流（行/秒/并行度），仅作用于初始回填阶段，保护源库与集群（固定保守值） */
    private static final int MV_BACKFILL_RATE_LIMIT = 50000;

    @Override
    public String engineType() {
        return ENGINE_TYPE;
    }

    @Override
    public boolean supports(String dbType) {
        return ENGINE_TYPE.equalsIgnoreCase(dbType);
    }

    @Override
    public List<CheckResult> precheck(SourceSpec spec, String viewName) {
        List<CheckResult> results = new ArrayList<>();
        try (Connection conn = engineConnection(spec.engineConnectId())) {
            String version = queryString(conn, "SELECT version()");
            results.add(new CheckResult("engine-connectivity", true, "RisingWave 连接正常：" + version));
        } catch (Exception e) {
            results.add(new CheckResult("engine-connectivity", false, "引擎连接失败：" + e.getMessage()));
        }
        if (StringUtils.isNotBlank(viewName)) {
            try {
                boolean exists = relationExists(spec.engineConnectId(), viewName);
                results.add(new CheckResult("view-name-status", true,
                        exists ? "引擎内已存在同名物化视图（已启用或可重建，初始化时可传 recreate 重建）：" + viewName
                                : "物化视图名可用：" + viewName));
            } catch (Exception e) {
                results.add(new CheckResult("view-name-status", false, "无法检查视图名状态：" + e.getMessage()));
            }
        }
        results.add(new CheckResult("origin-db-type", "mysql".equalsIgnoreCase(spec.originDbType()),
                "mysql-cdc 通道要求源库为 MySQL，当前：" + spec.originDbType()));
        try (Connection conn = originConnection(spec)) {
            results.addAll(precheckOrigin(conn, spec));
        } catch (Exception e) {
            results.add(new CheckResult("origin-connectivity", false,
                    "源库连接失败（" + spec.originHost() + ":" + spec.originPort() + "/" + spec.originDatabase() + "）：" + e.getMessage()));
        }
        return results;
    }

    private List<CheckResult> precheckOrigin(Connection conn, SourceSpec spec) throws SQLException {
        List<CheckResult> results = new ArrayList<>();
        try {
            String binlogFormat = queryString(conn, "SELECT @@binlog_format");
            results.add(new CheckResult("mysql-binlog-format", "ROW".equalsIgnoreCase(binlogFormat),
                    "binlog_format=" + binlogFormat + "（要求 ROW）"));
        } catch (SQLException e) {
            results.add(new CheckResult("mysql-binlog-format", false, "无法读取 binlog_format：" + e.getMessage()));
        }
        try {
            String rowImage = queryString(conn, "SELECT @@binlog_row_image");
            results.add(new CheckResult("mysql-binlog-row-image", "FULL".equalsIgnoreCase(rowImage),
                    "binlog_row_image=" + rowImage + "（要求 FULL）"));
        } catch (SQLException e) {
            results.add(new CheckResult("mysql-binlog-row-image", false, "无法读取 binlog_row_image：" + e.getMessage()));
        }
        List<String> grants = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
            while (rs.next()) {
                grants.add(rs.getString(1));
            }
        }
        String grantText = String.join(" ; ", grants);
        boolean allPrivileges = grantText.contains("ALL PRIVILEGES");
        boolean replicationSlave = allPrivileges || grantText.contains("REPLICATION SLAVE");
        boolean replicationClient = allPrivileges || grantText.contains("REPLICATION CLIENT");
        results.add(new CheckResult("mysql-replication-grants", replicationSlave && replicationClient,
                "REPLICATION SLAVE=" + replicationSlave + "，REPLICATION CLIENT=" + replicationClient
                        + "（授权：" + StringUtils.abbreviate(grantText, 200) + "）"));
        Map<String, String> actualTypes = new LinkedHashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(spec.tables().size(), "?"));
        String tableSql = "SELECT lower(table_name), table_type FROM information_schema.tables WHERE table_schema = ? AND lower(table_name) IN ("
                + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(tableSql)) {
            ps.setString(1, spec.originDatabase());
            int index = 2;
            for (String table : spec.tables()) {
                ps.setString(index++, table.toLowerCase(Locale.ROOT));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    actualTypes.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        List<String> notExist = new ArrayList<>();
        List<String> notPhysical = new ArrayList<>();
        for (String table : spec.tables()) {
            String type = actualTypes.get(table.toLowerCase(Locale.ROOT));
            if (type == null) {
                notExist.add(table);
            } else if (!"BASE TABLE".equalsIgnoreCase(type)) {
                notPhysical.add(table + "(" + type + ")");
            }
        }
        results.add(new CheckResult("origin-tables-physical", notExist.isEmpty() && notPhysical.isEmpty(),
                notExist.isEmpty() && notPhysical.isEmpty()
                        ? "源表全部为物理表：" + spec.tables()
                        : (notExist.isEmpty() ? "" : "源库中不存在（database=" + spec.originDatabase() + "）：" + notExist + "；")
                        + (notPhysical.isEmpty() ? "" : "非物理表（mysql-cdc 无法摄取 MySQL 视图，建表语句须引用物理表）：" + notPhysical)));
        return results;
    }

    @Override
    public DdlPlan buildDdlPlan(SourceSpec spec, String viewName, String engineSql) {
        List<CdcTableDdl> cdcTables = new ArrayList<>();
        try (Connection origin = originConnection(spec)) {
            for (String table : spec.tables()) {
                String cdcTable = cdcTableName(spec, table);
                try {
                    SourceTableSchema schema = fetchTableSchema(origin, spec.originDatabase(), table);
                    cdcTables.add(new CdcTableDdl(table, cdcTable, buildCdcTableDdl(schema, cdcTable, spec, table), null));
                } catch (RuntimeException e) {
                    // 单表内省失败不阻断整体预览：记录原因，由该段校验项提示用户
                    cdcTables.add(new CdcTableDdl(table, cdcTable, "", e.getMessage()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("连接源库内省表结构失败：" + e.getMessage(), e);
        }
        String mvDdl = StringUtils.isBlank(engineSql) ? "" : buildMvDdl(viewName, engineSql);
        return new DdlPlan(buildSourceDdl(spec), cdcTables, mvDdl, engineSql == null ? "" : engineSql);
    }

    /** CDC 摄取表名规则：<sourceName>_<源表名> */
    private String cdcTableName(SourceSpec spec, String table) {
        return (spec.sourceName() + "_" + table).toLowerCase(Locale.ROOT);
    }

    /**
     * 第①步：建 CDC source。密码以掩码输出，执行前再注入真实密码，避免密码外泄到前端与日志。
     * <p>server.id 显式指定（确定性派生，同一 source 稳定不变），避免多 source 共存同一 MySQL 时随机 ID 冲突。</p>
     */
    private String buildSourceDdl(SourceSpec spec) {
        return "CREATE SOURCE IF NOT EXISTS " + quoted(spec.sourceName()) + " WITH (\n"
                + "  connector = 'mysql-cdc',\n"
                + "  hostname = '" + sqlLiteral(spec.originHost()) + "',\n"
                + "  port = '" + spec.originPort() + "',\n"
                + "  username = '" + sqlLiteral(spec.originUserName()) + "',\n"
                + "  password = '" + RisingWaveDdlParser.PASSWORD_MASK + "',\n"
                + "  database.name = '" + sqlLiteral(spec.originDatabase()) + "',\n"
                + "  server.id = '" + serverIdOf(spec) + "'\n"
                + ")";
    }

    /** server.id：由源库连接与 source 名确定性派生（10000~1010000），同一 source 稳定，不同 source 冲突概率低 */
    static int serverIdOf(SourceSpec spec) {
        String seed = spec.originHost() + ":" + spec.originPort() + "/" + spec.originDatabase() + "/" + spec.sourceName();
        return 10000 + Math.abs(seed.hashCode()) % 1_000_000;
    }

    /** 第③步：建物化视图。source_rate_limit 仅作用于回填期，控制初始回填对源库与集群的压力 */
    private String buildMvDdl(String viewName, String sqlBody) {
        return "CREATE MATERIALIZED VIEW " + quoted(viewName) + " WITH ( source_rate_limit = " + MV_BACKFILL_RATE_LIMIT
                + " ) AS " + sqlBody;
    }

    @Override
    public DdlValidation validateDdl(SourceSpec spec, String viewName, DdlPlan edited) {
        List<StepCheck> checks = new ArrayList<>();
        checks.add(validateSourceStep(spec, edited));
        for (CdcTableDdl cdcTable : edited.cdcTables()) {
            checks.add(validateCdcTableStep(spec, cdcTable));
        }
        checks.add(validateMvStep(spec, viewName, edited));
        boolean passed = checks.stream().allMatch(StepCheck::passed);
        DdlPlan normalized = new DdlPlan(edited.sourceDdl(), edited.cdcTables(), edited.mvDdl(), mvBodyOf(edited));
        return new DdlValidation(passed, normalized, checks);
    }

    private StepCheck validateSourceStep(SourceSpec spec, DdlPlan plan) {
        List<CheckResult> items = new ArrayList<>();
        items.addAll(RisingWaveDdlParser.validateSource(plan.sourceDdl(), spec.sourceName(), spec.originDatabase()));
        try {
            boolean exists = relationExists(spec.engineConnectId(), spec.sourceName());
            items.add(new CheckResult("source-exists", true, exists
                    ? "引擎内已存在同名 source（将复用；对本段的修改不会重建已存在的 source）"
                    : "引擎内不存在同名 source（初始化时创建）"));
        } catch (Exception e) {
            items.add(new CheckResult("source-exists", false, "无法检查引擎内 source 是否存在：" + e.getMessage()));
        }
        try (Connection conn = originConnection(spec)) {
            items.addAll(precheckOrigin(conn, spec));
        } catch (Exception e) {
            items.add(new CheckResult("origin-connectivity", false, "源库连接失败：" + e.getMessage()));
        }
        return new StepCheck("source", allPassed(items), "FULL", items);
    }

    private StepCheck validateCdcTableStep(SourceSpec spec, CdcTableDdl cdcTable) {
        List<CheckResult> items = new ArrayList<>();
        if (cdcTable.buildError() != null) {
            items.add(new CheckResult("cdc-build", false, cdcTable.buildError()));
            return new StepCheck("cdc:" + cdcTable.originTable(), false, "FULL", items);
        }
        List<String> sourceColumns = new ArrayList<>();
        try (Connection origin = originConnection(spec)) {
            SourceTableSchema schema = fetchTableSchema(origin, spec.originDatabase(), cdcTable.originTable());
            schema.columns().forEach(column -> sourceColumns.add(column.name()));
        } catch (Exception e) {
            items.add(new CheckResult("cdc-source-schema", false, "源库内省表结构失败：" + e.getMessage()));
        }
        items.addAll(RisingWaveDdlParser.validateCdcTable(cdcTable.ddl(), cdcTable.engineTable(), spec.sourceName(),
                spec.originDatabase(), cdcTable.originTable(), sourceColumns));
        try {
            boolean exists = relationExists(spec.engineConnectId(), cdcTable.engineTable());
            items.add(new CheckResult("cdc-exists", true, exists
                    ? "引擎内已存在该 CDC 摄取表（将复用；对本段的修改不会生效，如需生效请先删除引擎内该表）"
                    : "引擎内不存在该 CDC 摄取表（初始化时创建）"));
        } catch (Exception e) {
            items.add(new CheckResult("cdc-exists", false, "无法检查引擎内 CDC 摄取表是否存在：" + e.getMessage()));
        }
        return new StepCheck("cdc:" + cdcTable.originTable(), allPassed(items), "FULL", items);
    }

    private StepCheck validateMvStep(SourceSpec spec, String viewName, DdlPlan plan) {
        List<CheckResult> items = new ArrayList<>();
        List<String> cdcTables = plan.cdcTables().stream().map(CdcTableDdl::engineTable).toList();
        items.addAll(RisingWaveDdlParser.validateMv(plan.mvDdl(), viewName, cdcTables));
        boolean allExist = !plan.cdcTables().isEmpty();
        for (CdcTableDdl cdcTable : plan.cdcTables()) {
            try {
                if (!relationExists(spec.engineConnectId(), cdcTable.engineTable())) {
                    allExist = false;
                    break;
                }
            } catch (Exception e) {
                allExist = false;
                break;
            }
        }
        if (allExist) {
            try {
                validateQuery(spec.engineConnectId(), mvBodyOf(plan));
                items.add(new CheckResult("mv-syntax", true, "EXPLAIN 校验通过：RisingWave 可解析该查询"));
            } catch (Exception e) {
                items.add(new CheckResult("mv-syntax", false, e.getMessage()));
            }
            return new StepCheck("mv", allPassed(items), "FULL", items);
        }
        items.add(new CheckResult("mv-syntax", true,
                "CDC 摄取表尚未在引擎内创建，完整语法校验将在初始化时执行（初始化前会再执行一次 EXPLAIN）"));
        return new StepCheck("mv", allPassed(items), "PENDING", items);
    }

    /** 物化视图 DDL 中 AS 之后的查询体；解析失败时回退到 plan 中携带的查询体 */
    private static String mvBodyOf(DdlPlan plan) {
        try {
            return RisingWaveDdlParser.parseMvDdl(plan.mvDdl()).body();
        } catch (RuntimeException e) {
            return plan.mvBody();
        }
    }

    private static boolean allPassed(List<CheckResult> items) {
        return items.stream().allMatch(CheckResult::passed);
    }

    @Override
    public SourceTableMapping applyDdl(SourceSpec spec, DdlPlan plan) {
        DataSource ds = engineDataSource(spec.engineConnectId());
        applySourceStep(spec, plan, ds);
        Map<String, String> mapping = new LinkedHashMap<>();
        for (CdcTableDdl cdcTable : plan.cdcTables()) {
            applyCdcTableStep(spec, cdcTable, ds);
            mapping.put(cdcTable.originTable(), cdcTable.engineTable());
        }
        if (!waitForRelations(ds, mapping.values(), AUTO_TABLE_WAIT_MILLIS)) {
            throw new IllegalStateException(String.format(
                    "CDC 摄取表 %s 创建后 %dms 内未出现在引擎目录；请检查 RisingWave 日志与源库账号权限",
                    mapping.values(), AUTO_TABLE_WAIT_MILLIS));
        }
        return new SourceTableMapping(mapping);
    }

    /** 第①步：建 CDC source（幂等，已存在则复用）；返回是否新建 */
    private boolean applySourceStep(SourceSpec spec, DdlPlan plan, DataSource ds) {
        if (relationExists(spec.engineConnectId(), spec.sourceName())) {
            log.info("[MV-CDC] 共享 CDC source 已存在，复用：{}", spec.sourceName());
            return false;
        }
        String ddl = injectPassword(plan.sourceDdl(), spec);
        log.info("[MV-CDC] 创建共享 CDC source {}（源 {}:{}/{}），DDL：\n{}",
                spec.sourceName(), spec.originHost(), spec.originPort(), spec.originDatabase(), maskCredentials(ddl));
        try {
            execute(ds, ddl);
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("创建 CDC source 失败（%s ← %s:%s/%s）：%s",
                    spec.sourceName(), spec.originHost(), spec.originPort(), spec.originDatabase(),
                    e.getMessage()), e);
        }
        return true;
    }

    /** 第②步：建 CDC 摄取表（幂等，已存在则复用）；返回是否新建 */
    private boolean applyCdcTableStep(SourceSpec spec, CdcTableDdl cdcTable, DataSource ds) {
        if (cdcTable.buildError() != null) {
            throw new IllegalStateException("无法生成 CDC 摄取表 " + cdcTable.engineTable() + " 的建表语句："
                    + cdcTable.buildError());
        }
        if (relationExists(spec.engineConnectId(), cdcTable.engineTable())) {
            log.info("[MV-CDC] CDC 摄取表已存在，复用：{} ← {}.{}", cdcTable.engineTable(), spec.originDatabase(), cdcTable.originTable());
            return false;
        }
        log.info("[MV-CDC] 创建 CDC 摄取表 {} ← {}.{}，DDL：\n{}", cdcTable.engineTable(),
                spec.originDatabase(), cdcTable.originTable(), cdcTable.ddl());
        try {
            execute(ds, cdcTable.ddl());
        } catch (SQLException e) {
            throw new IllegalStateException(String.format(
                    "创建 CDC 摄取表失败（%s ← %s.%s）：%s",
                    cdcTable.engineTable(), spec.originDatabase(), cdcTable.originTable(), e.getMessage()), e);
        }
        return true;
    }

    /**
     * 单步执行：source / cdc:&lt;originTable&gt; / mv 三选一执行，其余步骤不动。
     */
    @Override
    public StepResult applyStep(SourceSpec spec, DdlPlan plan, String stepKey) {
        DataSource ds = engineDataSource(spec.engineConnectId());
        if ("source".equals(stepKey)) {
            boolean created = applySourceStep(spec, plan, ds);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("sourceName", spec.sourceName());
            return new StepResult(stepKey, true, !created,
                    (created ? "CDC source 创建完成：" : "CDC source 已存在，复用：") + spec.sourceName(), detail);
        }
        if (stepKey != null && stepKey.startsWith("cdc:")) {
            String originTable = stepKey.substring("cdc:".length());
            CdcTableDdl cdc = plan.cdcTableOf(originTable);
            if (cdc == null) {
                throw new IllegalArgumentException("未找到源表 " + originTable + " 对应的 CDC 摄取表 DDL，请重新生成 DDL");
            }
            boolean created = applyCdcTableStep(spec, cdc, ds);
            if (!waitForRelations(ds, List.of(cdc.engineTable()), AUTO_TABLE_WAIT_MILLIS)) {
                throw new IllegalStateException(String.format(
                        "CDC 摄取表 %s 创建后 %dms 内未出现在引擎目录；请检查 RisingWave 日志与源库账号权限",
                        cdc.engineTable(), AUTO_TABLE_WAIT_MILLIS));
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("originTable", cdc.originTable());
            detail.put("engineTable", cdc.engineTable());
            return new StepResult(stepKey, true, !created,
                    (created ? "CDC 摄取表创建完成：" : "CDC 摄取表已存在，复用：") + cdc.engineTable(), detail);
        }
        if ("mv".equals(stepKey)) {
            applyMvDdl(spec, plan);
            return new StepResult(stepKey, true, false, "物化视图创建完成", new LinkedHashMap<>());
        }
        throw new IllegalArgumentException("未知步骤：" + stepKey);
    }

    @Override
    public void applyMvDdl(SourceSpec spec, DdlPlan plan) {
        String ddl = plan.mvDdl();
        log.info("[MV] 创建物化视图，DDL：\n{}", ddl);
        try {
            // 同会话开启 BACKGROUND_DDL：CREATE MV 立即返回，不阻塞到回填完成（回填进度由 viewStatus 轮询）
            executeInSession(engineDataSource(spec.engineConnectId()), "SET BACKGROUND_DDL = true", ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("创建物化视图失败：" + e.getMessage(), e);
        }
    }

    /** 执行前把 DDL 中的密码掩码替换为真实密码；用户自行填写的密码保持原样 */
    private String injectPassword(String ddl, SourceSpec spec) {
        if (ddl == null) {
            return null;
        }
        return ddl.replaceAll("(?i)(password\\s*=\\s*)'" + Pattern.quote(RisingWaveDdlParser.PASSWORD_MASK) + "'",
                "$1'" + Matcher.quoteReplacement(sqlLiteral(spec.originPassword())) + "'");
    }

    record SourceColumn(String name, String dataType, boolean unsigned, Long charLength, Integer precision, Integer scale) {
    }

    record SourceTableSchema(List<SourceColumn> columns, List<String> primaryKeys) {
    }

    private SourceTableSchema fetchTableSchema(Connection conn, String database, String table) throws SQLException {
        List<SourceColumn> columns = new ArrayList<>();
        String sql = "SELECT column_name, column_type, character_maximum_length, numeric_precision, numeric_scale "
                + "FROM information_schema.columns WHERE table_schema = ? AND lower(table_name) = ? ORDER BY ordinal_position";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, database);
            ps.setString(2, table.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnType = String.valueOf(rs.getString(2)).toLowerCase(Locale.ROOT);
                    String dataType = columnType.split("[\\s(]")[0];
                    boolean unsigned = columnType.contains("unsigned");
                    columns.add(new SourceColumn(rs.getString(1), dataType, unsigned,
                            longOrNull(rs.getObject(3)), intOrNull(rs.getObject(4)), intOrNull(rs.getObject(5))));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("源库 " + database + " 中未内省到表 " + table + " 的列定义");
        }
        String pkSql = "SELECT column_name FROM information_schema.key_column_usage "
                + "WHERE table_schema = ? AND lower(table_name) = ? AND constraint_name = 'PRIMARY' ORDER BY ordinal_position";
        List<String> primaryKeys = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
            ps.setString(1, database);
            ps.setString(2, table.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString(1));
                }
            }
        }
        if (primaryKeys.isEmpty()) {
            throw new IllegalStateException("源表 " + database + "." + table + " 无主键，mysql-cdc 摄取要求主键（upsert 键）；请先为源表添加主键");
        }
        return new SourceTableSchema(columns, primaryKeys);
    }

    String buildCdcTableDdl(SourceTableSchema schema, String cdcTable, SourceSpec spec, String table) {
        StringBuilder cols = new StringBuilder();
        for (SourceColumn col : schema.columns()) {
            if (cols.length() > 0) cols.append(", ");
            cols.append(quoted(col.name().toLowerCase(Locale.ROOT))).append(' ').append(mysqlToRwType(col));
        }
        String pk = schema.primaryKeys().stream().map(k -> quoted(k.toLowerCase(Locale.ROOT))).reduce((a, b) -> a + ", " + b).orElse("");
        cols.append(", PRIMARY KEY (").append(pk).append(")");
        // snapshot=false 跳过初始快照回填（大表场景避免源库与集群压力，靠 binlog 追平，期间数据不完整）；
        // WITH 子句按 RW 语法位于列定义与 FROM 之间
        return "CREATE TABLE IF NOT EXISTS " + quoted(cdcTable) + " (" + cols + ")"
                + " WITH ( snapshot = '" + spec.snapshotEnabled() + "' )"
                + " FROM " + quoted(spec.sourceName())
                + " TABLE '" + sqlLiteral(spec.originDatabase()) + "." + sqlLiteral(table) + "'";
    }

    // MySQL→RW 类型映射：unsigned 数值宽化一档；varchar 无长度；datetime→timestamp（非 timestamptz）
    static String mysqlToRwType(SourceColumn col) {
        String type = col.dataType() == null ? "" : col.dataType().trim().toLowerCase(Locale.ROOT);
        boolean u = col.unsigned();
        switch (type) {
            case "tinyint": return "smallint";
            case "smallint": return u ? "integer" : "smallint";
            case "mediumint":
            case "int":
            case "integer":
            case "year": return u ? "bigint" : "integer";
            case "bigint": return u ? "numeric" : "bigint";
            case "float": return "real";
            case "double":
            case "real": return "double precision";
            case "decimal":
            case "numeric": return "numeric";
            case "bit": return col.precision() != null && col.precision() > 1 ? "bytea" : "boolean";
            case "char":
            case "varchar": return "varchar";
            case "binary":
            case "varbinary":
            case "blob":
            case "tinyblob":
            case "mediumblob":
            case "longblob":
            case "geometry": return "bytea";
            case "date": return "date";
            case "datetime": return "timestamp";
            case "timestamp": return "timestamptz";
            case "time": return "time";
            case "json": return "jsonb";
            default: return "text";
        }
    }

    private static Long longOrNull(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static Integer intOrNull(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }

    private boolean waitForRelations(DataSource ds, Collection<String> lowerNames, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (relationNames(ds, lowerNames).keySet().containsAll(lowerNames)) {
                    return true;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("查询引擎内省目录失败：" + e.getMessage(), e);
            }
            try {
                Thread.sleep(AUTO_TABLE_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待摄取通道自动表时被中断", ie);
            }
        }
        return false;
    }

    @Override
    public void validateQuery(String engineConnectId, String sql) {
        DataSource ds = engineDataSource(engineConnectId);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
            while (rs.next()) { /* 只关心是否报错，不需要结果 */ }
            log.info("[MV-Validate] SQL 语法验证通过（EXPLAIN）");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "SQL 语法验证失败（RisingWave 不支持该语法，请将 MySQL 方言改为 RW/PG 兼容写法）：" + e.getMessage(), e);
        }
    }

    @Override
    public List<String> probeOutputColumns(String engineConnectId, String sql) {
        String body = sql == null ? "" : sql.trim();
        if (body.endsWith(";")) {
            body = body.substring(0, body.length() - 1);
        }
        String probe = "SELECT * FROM (" + body + ") AS _mv_default_column_probe LIMIT 0";
        DataSource ds = engineDataSource(engineConnectId);
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(probe)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            return columns;
        } catch (SQLException e) {
            throw new IllegalStateException("物化视图定义输出列探测失败：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean relationExists(String engineConnectId, String relationName) {
        DataSource ds = engineDataSource(engineConnectId);
        try {
            return !queryRows(ds, "SELECT name FROM rw_relations WHERE lower(name) = ?",
                    relationName.toLowerCase(Locale.ROOT)).isEmpty();
        } catch (SQLException e) {
            try {
                return !queryRows(ds, "SELECT table_name AS name FROM information_schema.tables WHERE lower(table_name) = ?",
                        relationName.toLowerCase(Locale.ROOT)).isEmpty();
            } catch (SQLException e2) {
                throw new IllegalStateException("无法查询引擎关系目录（rw_relations/information_schema 均失败）：" + e2.getMessage(), e2);
            }
        }
    }

    @Override
    public ViewStatus viewStatus(String engineConnectId, String schema, String viewName) {
        DataSource ds = engineDataSource(engineConnectId);
        List<Map<String, Object>> rows;
        try {
            rows = queryRows(ds, "SELECT * FROM rw_materialized_views WHERE lower(name) = ?",
                    viewName.toLowerCase(Locale.ROOT));
        } catch (SQLException e) {
            throw new IllegalStateException("查询物化视图状态失败（rw_materialized_views, " + viewName + "）：" + e.getMessage(), e);
        }
        if (rows.isEmpty()) {
            boolean existsAsRelation = relationExists(engineConnectId, viewName);
            if (!existsAsRelation) {
                return new ViewStatus(false, "NOT_EXISTS", null, "物化视图不存在", null);
            }
            return new ViewStatus(true, "UNKNOWN", null,
                    "存在于关系目录但未出现在 rw_materialized_views（引擎版本差异）", null);
        }
        Map<String, Object> row = rows.get(0);
        String definition = row.get("definition") == null ? null : String.valueOf(row.get("definition"));
        // 官方目录列 status = StreamJobStatus（Creating=创建/回填中, Created=已就绪），旧版本可能缺失
        String status = row.get("status") == null ? null : String.valueOf(row.get("status"));
        if (status != null) {
            String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
            if ("created".equals(normalizedStatus)) {
                return new ViewStatus(true, "READY", null, "回填完成，增量维护中", definition);
            }
            if ("creating".equals(normalizedStatus)) {
                String progress = ddlProgressOf(ds, viewName);
                return new ViewStatus(true, "BACKFILLING", progress, "初始回填进行中，此期间查询结果不完整", definition);
            }
        }
        // 旧版本无 status 列：尝试 backfill_progress（个别版本提供）与 rw_ddl_progress（仅有进行中 DDL 时才有行）
        Object progressValue = row.get("backfill_progress");
        String progress = progressValue == null ? null : String.valueOf(progressValue);
        if (progress == null) {
            progress = ddlProgressOf(ds, viewName);
        }
        if (progress == null) {
            // rw_ddl_progress 为空说明当前无进行中的回填任务，结合 initialized_at（初始化完成时间）判定就绪
            if (row.get("initialized_at") != null) {
                return new ViewStatus(true, "READY", null, "回填完成，增量维护中", definition);
            }
            return new ViewStatus(true, "UNKNOWN", null,
                    "无法从引擎目录判断回填进度（rw_materialized_views.status / rw_ddl_progress 均不可用）", definition);
        }
        String normalized = progress.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        boolean done = normalized.contains("\"progress\":1.0")
                || normalized.contains("\"progress\":1")
                || normalized.matches(".*[^0-9.]1(\\.0)?[^0-9.].*")
                || normalized.equals("1.0") || normalized.equals("1");
        return new ViewStatus(true, done ? "READY" : "BACKFILLING", progress,
                done ? "回填完成，增量维护中" : "初始回填进行中，此期间查询结果不完整", definition);
    }

    @Override
    public List<EngineColumn> introspectColumns(String engineConnectId, String schema, String viewName) {
        DataSource ds = engineDataSource(engineConnectId);
        try {
            return introspectByInformationSchema(ds, schema, viewName);
        } catch (SQLException e) {
            log.warn("information_schema.columns 内省失败，回退 JDBC 元数据（{}）：{}", viewName, e.getMessage());
            try {
                return introspectByJdbcMetaData(ds, schema, viewName);
            } catch (SQLException e2) {
                throw new IllegalStateException("物化视图列内省失败（" + viewName + "）：" + e2.getMessage(), e2);
            }
        }
    }

    private List<EngineColumn> introspectByInformationSchema(DataSource ds, String schema, String viewName) throws SQLException {
        String sql = "SELECT column_name, udt_name, data_type, is_nullable, character_maximum_length, "
                + "numeric_precision, numeric_scale, ordinal_position FROM information_schema.columns "
                + "WHERE lower(table_name) = ?" + (StringUtils.isBlank(schema) ? "" : " AND table_schema = ?")
                + " ORDER BY ordinal_position";
        List<EngineColumn> columns = new ArrayList<>();
        List<Map<String, Object>> rows = StringUtils.isBlank(schema)
                ? queryRows(ds, sql, viewName.toLowerCase(Locale.ROOT))
                : queryRows(ds, sql, viewName.toLowerCase(Locale.ROOT), schema);
        for (Map<String, Object> row : rows) {
            String typeName = row.get("udt_name") != null ? String.valueOf(row.get("udt_name"))
                    : String.valueOf(row.get("data_type"));
            columns.add(new EngineColumn(
                    String.valueOf(row.get("column_name")),
                    normalizeColumnTypeName(typeName),
                    !"NO".equalsIgnoreCase(String.valueOf(row.get("is_nullable"))),
                    intValue(row.get("character_maximum_length")),
                    intValue(row.get("numeric_precision")),
                    intValue(row.get("numeric_scale")),
                    intValue(row.get("ordinal_position")),
                    null));
        }
        return columns;
    }

    /**
     * 把 RisingWave/PG 风格类型名规范化为 MySQL 兼容类型名，
     * 保证 MysqlToJavaEnum 等平台侧类型映射可识别（fieldType 不为 null）。
     */
    private String normalizeColumnTypeName(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return typeName;
        }
        return switch (typeName.toLowerCase(Locale.ROOT)) {
            case "int2", "smallint" -> "SMALLINT";
            case "int4", "int", "integer" -> "INT";
            case "int8", "bigint", "long" -> "BIGINT";
            case "float4", "real" -> "FLOAT";
            case "float8", "double precision" -> "DOUBLE";
            case "numeric", "decimal" -> "DECIMAL";
            case "bpchar" -> "CHAR";
            case "varchar", "text", "string" -> "VARCHAR";
            case "bool", "boolean" -> "BIT";
            case "date" -> "DATE";
            case "time", "time without time zone" -> "TIME";
            case "timestamp", "timestamp without time zone" -> "DATETIME";
            case "timestamptz", "timestamp with time zone" -> "TIMESTAMP";
            case "json", "jsonb", "variant", "struct", "array" -> "JSON";
            case "bytea" -> "BLOB";
            default -> typeName.toUpperCase(Locale.ROOT);
        };
    }

    private List<EngineColumn> introspectByJdbcMetaData(DataSource ds, String schema, String viewName) throws SQLException {
        List<EngineColumn> columns = new ArrayList<>();
        try (Connection conn = ds.getConnection()) {
            try (ResultSet rs = conn.getMetaData().getColumns(
                    null, StringUtils.isBlank(schema) ? null : schema, viewName, "%")) {
                while (rs.next()) {
                    columns.add(new EngineColumn(
                            rs.getString("COLUMN_NAME"),
                            normalizeColumnTypeName(rs.getString("TYPE_NAME")),
                            rs.getInt("NULLABLE") != ResultSetMetaData.columnNoNulls,
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("DECIMAL_DIGITS"),
                            rs.getInt("ORDINAL_POSITION"),
                            rs.getString("REMARKS")));
                }
            }
        }
        return columns;
    }

    @Override
    public void dropView(String engineConnectId, String schema, String viewName) {
        try {
            execute(engineDataSource(engineConnectId), "DROP MATERIALIZED VIEW IF EXISTS " + quoted(viewName));
        } catch (SQLException e) {
            throw new IllegalStateException("删除物化视图失败（" + viewName + "）：" + e.getMessage(), e);
        }
    }

    @Override
    public void dropSource(String engineConnectId, String sourceName) {
        DataSource ds = engineDataSource(engineConnectId);
        List<String> tables = new ArrayList<>();
        try {
            for (Map<String, Object> row : queryRows(ds,
                    "SELECT table_name AS name FROM information_schema.tables WHERE table_name LIKE ?",
                    sourceName.toLowerCase(Locale.ROOT) + "\\_%")) {
                tables.add(String.valueOf(row.get("name")));
            }
            for (String table : tables) {
                execute(ds, "DROP TABLE IF EXISTS " + quoted(table));
            }
            execute(ds, "DROP SOURCE IF EXISTS " + quoted(sourceName));
            log.info("[MV-CDC] 删除摄取通道：source={}, 前缀 CDC 表={}", sourceName, tables);
        } catch (SQLException e) {
            throw new IllegalStateException("删除摄取通道失败（source=" + sourceName + "，前缀表 " + tables + "）：" + e.getMessage()
                    + "；若仍有物化视图引用这些 CDC 表，请先删除相关物化视图", e);
        }
    }

    private Connection engineConnection(String engineConnectId) throws SQLException {
        return engineDataSource(engineConnectId).getConnection();
    }

    private DataSource engineDataSource(String engineConnectId) {
        return PlatformDataSources.getDataSource(engineConnectId);
    }

    private Connection originConnection(SourceSpec spec) throws SQLException {
        String url = String.format(
                "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=30000",
                spec.originHost(), spec.originPort(), spec.originDatabase());
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("缺少 MySQL 驱动（com.mysql.cj.jdbc.Driver）", e);
        }
        return DriverManager.getConnection(url, spec.originUserName(), spec.originPassword());
    }

    private Map<String, String> relationNames(DataSource ds, Collection<String> lowerNames) throws SQLException {
        Map<String, String> result = new HashMap<>();
        if (lowerNames.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(lowerNames.size(), "?"));
        List<Map<String, Object>> rows;
        try {
            rows = queryRows(ds, "SELECT table_name AS name FROM information_schema.tables WHERE lower(table_name) IN (" + placeholders + ")",
                    lowerNames.toArray());
        } catch (SQLException e) {
            rows = queryRows(ds, "SELECT name FROM rw_relations WHERE lower(name) IN (" + placeholders + ")",
                    lowerNames.toArray());
        }
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("name"));
            result.put(name.toLowerCase(Locale.ROOT), name);
        }
        return result;
    }

    private String queryString(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private List<Map<String, Object>> queryRows(DataSource ds, String sql, Object... params) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return readRows(rs);
            }
        }
    }

    private void execute(DataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** 在同一会话内顺序执行多条语句（会话级 SET + DDL） */
    private void executeInSession(DataSource ds, String... statements) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /** 查询 rw_ddl_progress 中与本视图相关的 DDL 进度；引擎不支持该目录时静默返回 null */
    private String ddlProgressOf(DataSource ds, String viewName) {
        try {
            List<Map<String, Object>> rows = queryRows(ds,
                    "SELECT progress FROM rw_catalog.rw_ddl_progress WHERE lower(statement) LIKE ?",
                    "%" + viewName.toLowerCase(Locale.ROOT) + "%");
            return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("progress"));
        } catch (SQLException e) {
            log.debug("查询 rw_ddl_progress 失败（引擎版本可能不支持），忽略：{}", e.getMessage());
            return null;
        }
    }

    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        int index = 1;
        for (Object param : params) {
            ps.setObject(index++, param);
        }
    }

    private List<Map<String, Object>> readRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private int intValue(Object value) {
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    static String maskCredentials(String ddl) {
        return ddl == null ? null : ddl.replaceAll("(?i)(password\\s*=\\s*)'[^']*'", "$1'******'");
    }

    private String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String sqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
