package cn.geelato.archive.service;

import cn.geelato.archive.config.ArchiveProperties;
import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.enums.ExecutionModeEnum;
import cn.geelato.archive.enums.PolicyTypeEnum;
import cn.geelato.archive.enums.RunStatusEnum;
import cn.geelato.archive.enums.TargetTypeEnum;
import cn.geelato.archive.exception.ArchiveException;
import cn.geelato.archive.support.PolicyPresets;
import cn.geelato.archive.support.WhereConditionValidator;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 归档策略管理服务：CRUD、启用门禁校验（硬失败）、预置模板装载、实体"开启归档"集成。
 * <p>门禁失败抛 {@link ArchiveException#VALIDATION_ERROR}（70001），错误信息包含具体表/列/连接，
 * 便于精确诊断；绝不静默放过。</p>
 */
@Component
@Slf4j
public class ArchivePolicyService {

    /** 合法表名（拼入 SQL 前的防注入白名单校验） */
    private static final Pattern P_TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
    private static final String SOURCE_DEFAULT_TIME_COLUMN = "create_at";

    private final Dao dao;
    private final ArchiveRunService runService;
    private final ArchiveProperties properties;

    @Autowired
    public ArchivePolicyService(@Qualifier("primaryDao") Dao dao,
                                ArchiveRunService runService,
                                ArchiveProperties properties) {
        this.dao = dao;
        this.runService = runService;
        this.properties = properties;
    }

    // ==================== CRUD ====================

    public ApiPagedResult pageQuery(FilterGroup filterGroup, PageQueryRequest request) {
        return dao.pageQueryResult(ArchivePolicy.class, filterGroup, request);
    }

    public List<ArchivePolicy> query(FilterGroup filterGroup, String orderBy) {
        return dao.queryList(ArchivePolicy.class, filterGroup, orderBy);
    }

    public ArchivePolicy get(String id) {
        return dao.queryForObject(ArchivePolicy.class, id);
    }

    public ArchivePolicy getByCode(String code) {
        FilterGroup fg = new FilterGroup();
        fg.addFilter("code", code);
        List<ArchivePolicy> list = dao.queryList(ArchivePolicy.class, fg, null);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /** 创建或更新（基础必填与格式校验，启用门禁在 enable 时执行） */
    public ArchivePolicy createOrUpdate(ArchivePolicy form) {
        validateForm(form);
        fillDefaults(form);
        if (form.getId() == null || form.getId().isBlank()) {
            form.setId(UUID.randomUUID().toString().replace("-", ""));
            fillBaseFields(form);
            // 新建策略一律停用，显式启用才生效（无启用策略绝不动数据）
            form.setEnableStatus(0);
            dao.insert(form);
        } else {
            ArchivePolicy exists = dao.queryForObject(ArchivePolicy.class, form.getId());
            if (exists == null) {
                throw ArchiveException.validationError("归档策略不存在：" + form.getId());
            }
            if (exists.isEnabled()) {
                throw ArchiveException.validationError("策略启用中不允许修改，请先停用（policyId=" + form.getId() + "）");
            }
            form.setCreateAt(exists.getCreateAt());
            form.setCreator(exists.getCreator());
            form.setEnableStatus(exists.getEnableStatus());
            form.setLastRunId(exists.getLastRunId());
            form.setLastRunStatus(exists.getLastRunStatus());
            form.setUpdateAt(new Date());
            dao.update(form);
        }
        return form;
    }

    /** 软删（有进行中 run 拒绝） */
    public void isDelete(String id) {
        ArchivePolicy policy = get(id);
        if (policy == null) {
            throw ArchiveException.validationError("归档策略不存在：" + id);
        }
        if (runService.hasRunning(id)) {
            throw ArchiveException.policyRunningConflict("策略有正在运行的归档任务，禁止删除（policyId=" + id + "）");
        }
        policy.setDelStatus(1);
        policy.setDeleteAt(new Date());
        policy.setUpdateAt(new Date());
        dao.update(policy);
    }

    // ==================== 启用/停用（门禁） ====================

    /** 启用：先过全部门禁（硬失败），通过才置启用 */
    public ArchivePolicy enable(String id) {
        ArchivePolicy policy = get(id);
        if (policy == null) {
            throw ArchiveException.validationError("归档策略不存在：" + id);
        }
        if (policy.isEnabled()) {
            return policy;
        }
        validateForEnable(policy);
        policy.setEnableStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        policy.setUpdateAt(new Date());
        dao.update(policy);
        log.info("归档策略已启用：id={}, table={}, type={}", id, policy.getTableName(), policy.getPolicyType());
        return policy;
    }

    public ArchivePolicy disable(String id) {
        ArchivePolicy policy = get(id);
        if (policy == null) {
            throw ArchiveException.validationError("归档策略不存在：" + id);
        }
        if (runService.hasRunning(id)) {
            throw ArchiveException.policyRunningConflict("策略有正在运行的归档任务，请先中止再停用（policyId=" + id + "）");
        }
        policy.setEnableStatus(0);
        policy.setUpdateAt(new Date());
        dao.update(policy);
        return policy;
    }

    /**
     * 启用门禁（启用时执行；引擎每次 run 前亦复核关键项）。
     * 任一项不满足即抛 70001/70002，错误信息包含具体原因。
     */
    public void validateForEnable(ArchivePolicy policy) {
        // 1. 源表名格式与保护名单
        String table = requireTableName(policy);
        if (properties.isProtectedTable(table)) {
            throw ArchiveException.validationError("表 " + table + " 在归档保护名单内，禁止配置归档（核心数据防呆）");
        }
        // 2. 策略类型与条件
        PolicyTypeEnum type = PolicyTypeEnum.parse(policy.getPolicyType());
        if (type == null) {
            throw ArchiveException.validationError("未知策略类型：" + policy.getPolicyType() + "（允许 DEFAULT/CUSTOM）");
        }
        if (type == PolicyTypeEnum.DEFAULT) {
            if (policy.getRetentionDays() == null || policy.getRetentionDays() < properties.getMinRetentionDays()) {
                throw ArchiveException.validationError("保留天数必须不小于平台最小保留期 " + properties.getMinRetentionDays()
                        + " 天（当前：" + policy.getRetentionDays() + "）");
            }
        } else {
            if (policy.getWhereCondition() == null || policy.getWhereCondition().isBlank()) {
                throw ArchiveException.validationError("CUSTOM 策略必须配置自定义条件 whereCondition");
            }
            String reject = WhereConditionValidator.validate(policy.getWhereCondition());
            if (reject != null) {
                throw ArchiveException.validationError("自定义条件安全校验不通过：" + reject);
            }
        }
        // 3. 目标类型与执行模式
        TargetTypeEnum targetType = TargetTypeEnum.parse(policy.getTargetType());
        if (targetType == null || !TargetTypeEnum.supported(targetType)) {
            throw ArchiveException.validationError("目标类型不支持：" + policy.getTargetType() + "（一期仅 MYSQL）");
        }
        ExecutionModeEnum mode = ExecutionModeEnum.parse(policy.getExecutionMode());
        if (mode == null) {
            throw ArchiveException.validationError("未知执行模式：" + policy.getExecutionMode() + "（允许 AUTO/SERVER/LOCAL_FILE/JDBC）");
        }
        // 4. 源表物理存在
        if (!tableExists(table)) {
            throw ArchiveException.validationError("源表不存在：" + table);
        }
        // 5. DEFAULT 策略的 create_at 列存在且为日期类型
        if (type == PolicyTypeEnum.DEFAULT && !isDateColumn(table, SOURCE_DEFAULT_TIME_COLUMN)) {
            throw ArchiveException.validationError("表 " + table + " 缺少日期类型的 " + SOURCE_DEFAULT_TIME_COLUMN + " 列（DEFAULT 策略窗口依赖）");
        }
        // 6. 归档目标连接可达
        String connectId = policy.getConnectId();
        if (connectId != null && !connectId.isBlank()) {
            DataSource ds = DataSourceManager.singleInstance().getRegisteredDataSource(connectId);
            if (ds == null) {
                throw ArchiveException.validationError("归档库连接未注册：" + connectId + "（请在数据源管理中配置 dev_db_connect 后重试）");
            }
            try (Connection ignored = ds.getConnection()) {
                // 连通性探测
            } catch (SQLException e) {
                throw ArchiveException.validationError("归档库连接不可达：" + connectId + "，原因：" + e.getMessage(), e);
            }
        }
        // 7. 同库归档防自残：目标表必须与源表不同名
        String target = resolveTargetTableName(policy);
        if (!isValidIdentifier(target)) {
            throw ArchiveException.validationError("目标表名不合法：" + target);
        }
        if ((connectId == null || connectId.isBlank()) && target.equalsIgnoreCase(table)) {
            throw ArchiveException.validationError("同库归档的目标表不能与源表同名（防止归档器搬给自己后删除源数据），请指定 target_table_name");
        }
        // 8. 一表一启用策略
        FilterGroup fg = new FilterGroup();
        fg.addFilter("tableName", table);
        fg.addFilter("enableStatus", String.valueOf(ColumnDefault.ENABLE_STATUS_VALUE));
        List<ArchivePolicy> conflicts = dao.queryList(ArchivePolicy.class, fg, null);
        if (conflicts != null) {
            for (ArchivePolicy p : conflicts) {
                if (p.getId() != null && !p.getId().equals(policy.getId())) {
                    throw ArchiveException.validationError("表 " + table + " 已存在启用中的策略（policyId=" + p.getId() + "），一表仅允许一条启用策略");
                }
            }
        }
        // 9. CUSTOM 条件试跑（语法错误硬失败）
        if (type == PolicyTypeEnum.CUSTOM) {
            try {
                dao.getJdbcTemplate().execute("SELECT id FROM " + table + " WHERE (" + policy.getWhereCondition() + ") LIMIT 1");
            } catch (Exception e) {
                throw ArchiveException.validationError("自定义条件试跑失败（语法或列名错误）：" + policy.getWhereCondition()
                        + "，原因：" + e.getMessage(), e);
            }
        }
    }

    // ==================== 目标表名推导 ====================

    /** 空则推导：跨库（connectId 非空）→ 与源表同名；同库 → {源表}_archive */
    public String resolveTargetTableName(ArchivePolicy policy) {
        if (policy.getTargetTableName() != null && !policy.getTargetTableName().isBlank()) {
            return policy.getTargetTableName().trim();
        }
        String connectId = policy.getConnectId();
        if (connectId != null && !connectId.isBlank()) {
            return policy.getTableName();
        }
        return policy.getTableName() + "_archive";
    }

    // ==================== 实体"开启归档" ====================

    /**
     * 实体管理集成入口：按实体名解析物理表名，插入一条默认停用的 DEFAULT 策略。
     * 实体来自平台元数据（Java 类实体或设计器 DB 源实体均可）。
     */
    public ArchivePolicy enableArchiveForEntity(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            throw ArchiveException.validationError("实体名不能为空");
        }
        EntityMeta meta = MetaManager.singleInstance().get(entityName);
        if (meta == null) {
            throw ArchiveException.validationError("平台元数据中不存在实体：" + entityName + "（无实体定义的裸表不允许归档）");
        }
        String tableName = meta.getEntityName();
        if (properties.isProtectedTable(tableName)) {
            throw ArchiveException.validationError("表 " + tableName + " 在归档保护名单内，禁止开启归档");
        }
        // 同表已有未删策略则直接返回（幂等）
        FilterGroup fg = new FilterGroup();
        fg.addFilter("tableName", tableName);
        List<ArchivePolicy> existing = dao.queryList(ArchivePolicy.class, fg, null);
        if (existing != null && !existing.isEmpty()) {
            return existing.get(0);
        }
        ArchivePolicy policy = new ArchivePolicy();
        policy.setId(UUID.randomUUID().toString().replace("-", ""));
        policy.setName(meta.getEntityTitle() != null ? meta.getEntityTitle() + " 归档" : tableName + " 归档");
        policy.setCode("ent-" + tableName);
        policy.setDescription("由实体管理「开启归档」生成：实体 " + entityName + " → 表 " + tableName);
        policy.setTableName(tableName);
        policy.setSourceEntityName(entityName);
        policy.setPolicyType(PolicyTypeEnum.DEFAULT.name());
        policy.setRetentionDays(properties.getPresetAuditRetentionDays());
        policy.setEnableStatus(0);
        fillBaseFields(policy);
        dao.insert(policy);
        log.info("实体 {}（表 {}）已开启归档，生成默认停用策略 {}", entityName, tableName, policy.getId());
        return policy;
    }

    // ==================== 预置模板 ====================

    /** 启动装载预置模板（幂等：code 已存在跳过，不覆盖用户改动），返回本次新增数 */
    public int loadPresets() {
        int created = 0;
        for (PolicyPresets.Preset preset : PolicyPresets.PRESETS) {
            if (getByCode(preset.code) != null) {
                continue;
            }
            // 源表当前不存在（如邮件模块未部署）则跳过，不产生幽灵策略
            if (!tableExists(preset.tableName)) {
                log.debug("预置模板 {} 的源表 {} 当前不存在，跳过", preset.code, preset.tableName);
                continue;
            }
            ArchivePolicy policy = new ArchivePolicy();
            policy.setId(UUID.randomUUID().toString().replace("-", ""));
            policy.setCode(preset.code);
            policy.setName(preset.name);
            policy.setDescription("平台高膨胀表预置模板，核阅后启用");
            policy.setTableName(preset.tableName);
            policy.setPolicyType(PolicyPresets.PRESET_POLICY_TYPE);
            policy.setRetentionDays(preset.retentionDays);
            policy.setEnableStatus(0);
            fillBaseFields(policy);
            dao.insert(policy);
            created++;
        }
        if (created > 0) {
            log.info("已装载归档预置模板 {} 条（默认停用）", created);
        }
        return created;
    }

    // ==================== 策略运行状态回写 ====================

    public void updateLastRun(String policyId, String runId, String runStatus) {
        ArchivePolicy policy = get(policyId);
        if (policy == null) {
            return;
        }
        policy.setLastRunId(runId);
        policy.setLastRunStatus(runStatus);
        policy.setUpdateAt(new Date());
        dao.update(policy);
    }

    public List<ArchivePolicy> queryEnabledPolicies() {
        FilterGroup fg = new FilterGroup();
        fg.addFilter("enableStatus", String.valueOf(ColumnDefault.ENABLE_STATUS_VALUE));
        return dao.queryList(ArchivePolicy.class, fg, "seq_no asc");
    }

    public boolean hasRunning(String policyId) {
        return runService.hasRunning(policyId);
    }

    // ==================== 内部工具 ====================

    private String requireTableName(ArchivePolicy policy) {
        if (policy.getTableName() == null || policy.getTableName().isBlank()) {
            throw ArchiveException.validationError("源表名不能为空");
        }
        String table = policy.getTableName().trim();
        if (!P_TABLE_NAME.matcher(table).matches()) {
            throw ArchiveException.validationError("源表名不合法（仅允许字母数字下划线，长度 1-64）：" + table);
        }
        return table;
    }

    private void validateForm(ArchivePolicy form) {
        requireTableName(form);
        if (form.getTargetTableName() != null && !form.getTargetTableName().isBlank()
                && !isValidIdentifier(form.getTargetTableName().trim())) {
            throw ArchiveException.validationError("目标表名不合法：" + form.getTargetTableName());
        }
        if (form.getBatchSize() != null && (form.getBatchSize() < 50 || form.getBatchSize() > 5000)) {
            throw ArchiveException.validationError("每批行数必须在 50~5000 之间（当前：" + form.getBatchSize() + "）");
        }
        if (form.getWhereCondition() != null) {
            String reject = WhereConditionValidator.validate(form.getWhereCondition());
            if (reject != null) {
                throw ArchiveException.validationError("自定义条件安全校验不通过：" + reject);
            }
        }
    }

    private void fillDefaults(ArchivePolicy form) {
        if (form.getPolicyType() == null || form.getPolicyType().isBlank()) {
            form.setPolicyType(PolicyTypeEnum.DEFAULT.name());
        }
        if (form.getTargetType() == null || form.getTargetType().isBlank()) {
            form.setTargetType(TargetTypeEnum.MYSQL.name());
        }
        if (form.getExecutor() == null || form.getExecutor().isBlank()) {
            form.setExecutor("INLINE");
        }
        if (form.getExecutionMode() == null || form.getExecutionMode().isBlank()) {
            form.setExecutionMode(ExecutionModeEnum.AUTO.name());
        }
        if (form.getBatchSize() == null) {
            form.setBatchSize(properties.getDefaultBatchSize());
        }
        if (form.getBatchIntervalMs() == null) {
            form.setBatchIntervalMs(properties.getDefaultBatchIntervalMs());
        }
        if (form.getMaxRowsPerRun() == null) {
            form.setMaxRowsPerRun(properties.getDefaultMaxRowsPerRun());
        }
        if (form.getIncludeDeleted() == null) {
            form.setIncludeDeleted(Boolean.TRUE);
        }
        if (form.getConnectId() != null) {
            form.setConnectId(form.getConnectId().trim());
        }
    }

    private void fillBaseFields(ArchivePolicy policy) {
        Date now = new Date();
        if (policy.getCreateAt() == null) {
            policy.setCreateAt(now);
        }
        if (policy.getCreator() == null || policy.getCreator().isBlank()) {
            policy.setCreator("archive-admin");
        }
        if (policy.getUpdater() == null || policy.getUpdater().isBlank()) {
            policy.setUpdater("archive-admin");
        }
        if (policy.getUpdateAt() == null) {
            policy.setUpdateAt(now);
        }
    }

    private boolean isValidIdentifier(String name) {
        return name != null && P_TABLE_NAME.matcher(name).matches();
    }

    /** 源表物理存在（主库 DatabaseMetaData，大小写三试，照 MailSchemaInitializer 模式） */
    private boolean tableExists(String tableName) {
        return checkTableAndColumns(tableName, null);
    }

    /** 列存在且为日期类型 */
    private boolean isDateColumn(String tableName, String columnName) {
        return checkTableAndColumns(tableName, columnName);
    }

    private boolean checkTableAndColumns(String tableName, String dateColumn) {
        try (Connection connection = dao.getJdbcTemplate().getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            if (!tableExists(metaData, catalog, tableName)) {
                return false;
            }
            if (dateColumn == null) {
                return true;
            }
            try (ResultSet columns = metaData.getColumns(catalog, null, tableName, dateColumn)) {
                while (columns.next()) {
                    String typeName = columns.getString("TYPE_NAME");
                    if (typeName != null && (typeName.toUpperCase().contains("DATE")
                            || typeName.toUpperCase().contains("TIMESTAMP"))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw ArchiveException.validationError("检查源表 " + tableName + " 元数据失败：" + e.getMessage(), e);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        return tableExists(metaData, catalog, tableName, "TABLE")
                || tableExists(metaData, catalog, tableName.toUpperCase(), "TABLE")
                || tableExists(metaData, catalog, tableName.toLowerCase(), "TABLE");
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName, String type) throws SQLException {
        try (ResultSet tables = metaData.getTables(catalog, null, tableName, new String[]{type})) {
            return tables.next();
        }
    }

    /** 供引擎复核使用：run 前关键门禁复检（表存在 + 条件有效 + 策略未被删除） */
    public void revalidateBeforeRun(ArchivePolicy policy) {
        if (policy == null || !policy.isEnabled()) {
            throw ArchiveException.validationError("策略不存在或未启用");
        }
        if (properties.isProtectedTable(policy.getTableName())) {
            throw ArchiveException.validationError("表 " + policy.getTableName() + " 在归档保护名单内，拒绝执行");
        }
        if (!tableExists(policy.getTableName())) {
            throw ArchiveException.validationError("源表不存在：" + policy.getTableName());
        }
        if (PolicyTypeEnum.CUSTOM.name().equalsIgnoreCase(policy.getPolicyType())
                && policy.getWhereCondition() != null) {
            String reject = WhereConditionValidator.validate(policy.getWhereCondition());
            if (reject != null) {
                throw ArchiveException.validationError("自定义条件安全校验不通过：" + reject);
            }
        }
    }
}
