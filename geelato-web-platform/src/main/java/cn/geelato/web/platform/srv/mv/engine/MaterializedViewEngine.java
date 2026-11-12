package cn.geelato.web.platform.srv.mv.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 物化视图引擎 SPI。实现为 Spring Bean，由 supports(dbType) 按目标连接的 db_type 认领。
 * 新增引擎 = 实现本接口 + 数据源建池分支 + Dialects 别名注册。
 *
 * <p>建立物化视图统一为三步 DDL：①建 CDC source ②建 CDC 摄取表 ③建物化视图。
 * 三段 DDL 由 {@link #buildDdlPlan} 构造（只读内省，不执行），前端渲染并允许编辑，
 * 再由 {@link #validateDdl} 校验、{@link #applyDdl}/{@link #applyMvDdl} 执行，
 * 保证「前端看到的」与「引擎执行的」是同一份 DDL。</p>
 */
public interface MaterializedViewEngine {

    String engineType();

    boolean supports(String dbType);

    /** 逐项返回检查结果，不中途抛出 */
    List<CheckResult> precheck(SourceSpec spec, String viewName);

    /**
     * 构造建立物化视图的三步 DDL。只读内省源库，不连接引擎、不执行任何 DDL。
     *
     * @param engineSql 改写后的 SELECT（引擎方言），用于拼装第③步
     */
    DdlPlan buildDdlPlan(SourceSpec spec, String viewName, String engineSql);

    /**
     * 校验（可能被用户编辑过的）三步 DDL。
     *
     * @param edited 待校验的 DDL（自动生成结果或用户编辑结果）
     * @return 逐段校验结论；passed=true 时 normalized 为可直接执行的 DDL
     */
    DdlValidation validateDdl(SourceSpec spec, String viewName, DdlPlan edited);

    /** 执行第①②步（建 CDC source + CDC 摄取表）；幂等，已存在则复用；返回源表→引擎表映射 */
    SourceTableMapping applyDdl(SourceSpec spec, DdlPlan plan);

    /** 执行第③步（建物化视图）；是否先删重建由编排层显式参数决定 */
    void applyMvDdl(SourceSpec spec, DdlPlan plan);

    /**
     * 单步执行：只执行三步 DDL 中的某一步。
     * <p>step.key 与 {@link StepCheck#key()} 一致：source / cdc:&lt;originTable&gt; / mv。
     * 语义与 {@link #applyDdl}/{@link #applyMvDdl} 保持一致（幂等，已存在则复用）。</p>
     *
     * @param stepKey 步骤标识
     * @return 单步执行结果
     */
    default StepResult applyStep(SourceSpec spec, DdlPlan plan, String stepKey) {
        throw new UnsupportedOperationException("当前引擎不支持单步执行：" + engineType());
    }

    boolean relationExists(String engineConnectId, String relationName);

    ViewStatus viewStatus(String engineConnectId, String schema, String viewName);

    List<EngineColumn> introspectColumns(String engineConnectId, String schema, String viewName);

    void dropView(String engineConnectId, String schema, String viewName);

    /** 幂等；是否删除由编排层显式参数决定 */
    void dropSource(String engineConnectId, String sourceName);

    /** 语法预检：解析 SELECT 语句但不执行（RW 用 EXPLAIN），失败说明引擎不支持该 SQL 语法 */
    void validateQuery(String engineConnectId, String sql);

    /**
     * 试探查询的输出列名（零副作用）：执行 SELECT * FROM (sql) AS _mv_default_column_probe LIMIT 0，
     * 读取结果集元数据返回输出列名。用于物化视图平台默认字段强制校验（创建前探测最终输出列）。
     */
    List<String> probeOutputColumns(String engineConnectId, String sql);

    /** 密码为解密后明文，仅用于引擎侧建通道 */
    record SourceSpec(String engineConnectId,
                      String originConnectId, String originDbType,
                      String originHost, int originPort,
                      String originDatabase, String originUserName, String originPassword,
                      String sourceName, List<String> tables,
                      boolean snapshotEnabled) {
    }

    record CheckResult(String item, boolean passed, String message) {
    }

    /**
     * 三步 DDL：source 1 条 + 每个源表 1 条 CDC 摄取表 + 物化视图 1 条。
     *
     * @param mvBody 物化视图 DDL 中 AS 之后的查询体，供编排层做 EXPLAIN 预检
     */
    record DdlPlan(String sourceDdl, List<CdcTableDdl> cdcTables, String mvDdl, String mvBody) {

        public DdlPlan {
            cdcTables = cdcTables == null ? List.of() : List.copyOf(cdcTables);
        }

        /** 按 originTable 取 CDC 表 DDL */
        public CdcTableDdl cdcTableOf(String originTable) {
            if (originTable == null) {
                return null;
            }
            for (CdcTableDdl cdcTable : cdcTables) {
                if (originTable.equalsIgnoreCase(cdcTable.originTable())) {
                    return cdcTable;
                }
            }
            return null;
        }
    }

    /**
     * 一条 CDC 摄取表 DDL。
     *
     * @param buildError 内省源表结构失败时的原因（非空表示该表 DDL 无法生成，ddl 为空）
     */
    record CdcTableDdl(String originTable, String engineTable, String ddl, String buildError) {

        public CdcTableDdl(String originTable, String engineTable, String ddl) {
            this(originTable, engineTable, ddl, null);
        }
    }

    /** 分段校验结果，key 与前端 step.key 对应（source / cdc:&lt;originTable&gt; / mv） */
    record DdlValidation(boolean passed, DdlPlan normalized, List<StepCheck> checks) {

        public DdlValidation {
            checks = checks == null ? List.of() : List.copyOf(checks);
        }

        public StepCheck checkOf(String key) {
            for (StepCheck check : checks) {
                if (key.equals(check.key())) {
                    return check;
                }
            }
            return null;
        }
    }

    /**
     * 分段校验结果，key 与前端 step.key 对应（source / cdc:&lt;originTable&gt; / mv）。
     *
     * @param level FULL=已完整校验；PENDING=部分校验推迟到初始化时执行
     */
    record StepCheck(String key, boolean passed, String level, List<CheckResult> items) {

        public StepCheck {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** 单步执行结果 */
    record StepResult(String stepKey, boolean executed, boolean reused, String message, Map<String, Object> detail) {

        public StepResult {
            detail = detail == null ? Map.of() : new LinkedHashMap<>(detail);
        }
    }

    record SourceTableMapping(Map<String, String> originToEngine) {

        public SourceTableMapping {
            Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            caseInsensitive.putAll(originToEngine);
            originToEngine = caseInsensitive;
        }

        public Map<String, String> asMap() {
            return originToEngine;
        }

        public String engineTableOf(String originTable) {
            return originTable == null ? null : originToEngine.get(originTable);
        }
    }

    record EngineColumn(String name, String typeName, boolean nullable,
                        int charMaxLength, int numericPrecision, int numericScale,
                        int ordinalPosition, String comment) {
    }

    /** state ∈ BACKFILLING / READY / NOT_EXISTS / UNKNOWN */
    record ViewStatus(boolean exists, String state, String progress, String detail, String definition) {
    }
}
