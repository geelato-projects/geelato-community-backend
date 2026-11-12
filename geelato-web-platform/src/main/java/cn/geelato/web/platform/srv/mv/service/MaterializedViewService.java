package cn.geelato.web.platform.srv.mv.service;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.datasource.PlatformDataSources;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.util.EncryptUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.mv.MvSqlRewriter;
import cn.geelato.web.platform.srv.mv.MvViewExpander;
import cn.geelato.web.platform.srv.mv.engine.MaterializedViewEngine;
import cn.geelato.web.platform.srv.model.service.DevViewService;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 双记录架构：启用时在 platform_dev_view 新增同名 materialized 副本行（connect_id=引擎连接），
 * 原视图行不动。MetaManager 加载同名多行时 materialized 优先注册，实体路由直达引擎。
 * 停用=软删副本行（引擎保留），元数据回退原视图。
 */
@Component
@Slf4j
public class MaterializedViewService extends BaseService {

    private static final Pattern VIEW_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern IDENT_PART_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");

    /** 平台默认字段强制校验项 key（物化视图输出列必须包含 column_default.json 全部默认字段） */
    static final String DEFAULT_COLUMN_CHECK_ITEM = "mv-default-columns";

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final List<MaterializedViewEngine> engines;
    private final DevViewService devViewService;

    @Autowired
    public MaterializedViewService(List<MaterializedViewEngine> engines,
                                   DevViewService devViewService) {
        this.engines = engines;
        this.devViewService = devViewService;
    }

    public Map<String, Object> precheck(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        String engineConnectId = str(params, "engineConnectId");
        String originConnectId = str(params, "originConnectId");
        String viewName = str(params, "viewName");
        List<String> tables = strList(params, "tables");
        log.info("[MV-Precheck] 预检开始：view={}, engineConnectId={}, originConnectId={}, tables={}",
                viewName, engineConnectId, originConnectId, tables);
        List<MaterializedViewEngine.CheckResult> items = new ArrayList<>();
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        ConnectMeta originConnect = requireConnect(originConnectId, "originConnectId");
        MaterializedViewEngine engine = findEngine(engineConnect);
        if (engine == null) {
            items.add(new MaterializedViewEngine.CheckResult("engine-adapter", false,
                    String.format("连接 %s（db_type=%s）没有物化视图引擎适配器，已注册引擎：%s",
                            engineConnectId, engineConnect.getDbType(), engineTypes())));
        } else {
            items.add(new MaterializedViewEngine.CheckResult("engine-adapter", true,
                    "引擎适配器：" + engine.engineType()));
        }
        if (tables.isEmpty()) {
            items.add(new MaterializedViewEngine.CheckResult("tables-nonempty", false, "源表清单（tables）不能为空"));
        }
        if (engine != null && !tables.isEmpty()) {
            String database = resolveDatabase(params, originConnect);
            try {
                MvViewExpander.Expansion expansion = expandOrigin(originConnect, database, "select 1", tables);
                if (!expansion.expandedViews().isEmpty()) {
                    items.add(new MaterializedViewEngine.CheckResult("origin-views-expand", true,
                            "视图引用已展开为物理表：" + expansion.expandedViews() + " → " + expansion.physicalTables()));
                }
                String normalizedViewName = StringUtils.isBlank(viewName) ? "precheck" : normalizeViewName(viewName);
                MaterializedViewEngine.SourceSpec spec = buildSpec(engineConnectId, originConnect, database, expansion.physicalTables(), normalizedViewName, snapshotEnabledOf(params));
                items.addAll(engine.precheck(spec, viewName));
            } catch (Exception e) {
                items.add(new MaterializedViewEngine.CheckResult("origin-views-expand", false,
                        "解析/展开源库对象失败：" + e.getMessage()));
            }
        }
        boolean passed = items.stream().allMatch(MaterializedViewEngine.CheckResult::passed);
        for (MaterializedViewEngine.CheckResult item : items) {
            log.info("[MV-Precheck]   {} {}：{}", item.passed() ? "√" : "×", item.item(), item.message());
        }
        log.info("[MV-Precheck] 预检完成：view={}, 结果={}, 共 {} 项, 耗时={}ms",
                viewName, passed ? "通过" : "未通过", items.size(), System.currentTimeMillis() - startAt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("items", items);
        return result;
    }

    public Map<String, Object> createSource(Map<String, Object> params) {
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        String viewName = normalizeViewName(str(params, "viewName"));
        ConnectMeta originConnect = requireConnect(str(params, "originConnectId"), "originConnectId");
        List<String> tables = requireTables(params);
        String database = resolveDatabase(params, originConnect);
        MvViewExpander.Expansion expansion = expandOrigin(originConnect, database, "select 1", tables);
        List<String> physicalTables = expansion.physicalTables();
        if (physicalTables.isEmpty()) {
            throw new IllegalArgumentException("源表清单未解析到任何物理表（MySQL 视图会被自动展开）");
        }
        MaterializedViewEngine.SourceSpec spec = buildSpec(engineConnectId, originConnect, database, physicalTables, viewName, snapshotEnabledOf(params));
        MaterializedViewEngine.DdlPlan plan = engine.buildDdlPlan(spec, viewName, null);
        MaterializedViewEngine.SourceTableMapping mapping = engine.applyDdl(spec, plan);
        persistDdlToViewConstruct(engine, engineConnectId, viewName,
                buildDdlStorageJson(engine, spec, physicalTables, mapping.asMap(), plan, true));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engineType", engine.engineType());
        result.put("engineConnectId", engineConnectId);
        result.put("sourceName", spec.sourceName());
        result.put("physicalTables", physicalTables);
        result.put("tableMapping", mapping.asMap());
        result.put("ddl", ddlPayload(plan));
        return result;
    }

    public Map<String, Object> enable(Map<String, Object> params) {
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        String viewName = normalizeViewName(str(params, "viewName"));
        TableView originView = requireOriginView(viewName);
        String originSql = requireOriginSql(params, viewName);
        TableView materializedView = findAnyMaterializedView(viewName);
        String mode;
        if (materializedView == null) {
            materializedView = new TableView();
            materializedView.setAppId(originView.getAppId());
            materializedView.setTenantCode(originView.getTenantCode());
            materializedView.setEntityName(originView.getEntityName());
            materializedView.setTitle(originView.getTitle());
            materializedView.setSeqNo(originView.getSeqNo());
            mode = "created";
        } else {
            mode = "updated";
        }
        // 禁用后再启用：恢复软删行，避免启停循环积累记录
        materializedView.setDelStatus(cn.geelato.core.enums.DeleteStatusEnum.NO.getValue());
        materializedView.setDeleteAt(null);
        materializedView.setConnectId(engineConnectId);
        materializedView.setViewName(viewName);
        materializedView.setViewType(ViewTypeEnum.MATERIALIZED.getCode());
        materializedView.setViewConstruct(originSql);
        // JSON 列不能存空串
        materializedView.setViewColumn("[]");
        materializedView.setDescription(String.format("物化视图（引擎 %s，原视图 %s；待初始化）", engine.engineType(), viewName));
        materializedView.setEnableStatus(EnableStatusEnum.ENABLED.getValue());
        materializedView.setLinked(0);
        materializedView.afterSet();
        if ("created".equals(mode)) {
            devViewService.createModel(materializedView);
        } else {
            devViewService.updateModel(materializedView);
        }
        refreshMetaAndCache(viewName);
        log.info("[MV] 启用（仅保存副本行，未初始化引擎）：view={}, engineConnectId={}, 副本行={}, id={}",
                viewName, engineConnectId, mode, materializedView.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("viewType", ViewTypeEnum.MATERIALIZED.getCode());
        result.put("mode", mode);
        result.put("enabled", true);
        result.put("engineConnectId", engineConnectId);
        result.put("note", "物化视图记录已保存；尚未在引擎初始化（视图列待回填），请经 view/create 初始化后再承载查询");
        return result;
    }

    public Map<String, Object> createView(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        String viewName = normalizeViewName(str(params, "viewName"));
        TableView originView = requireOriginView(viewName);
        ConnectMeta originConnect = requireConnect(str(params, "originConnectId"), "originConnectId");
        String database = resolveDatabase(params, originConnect);
        String originSql = requireOriginSql(params, viewName);
        boolean recreate = Boolean.parseBoolean(String.valueOf(params.getOrDefault("recreate", "false")));
        TableView materializedView = findMaterializedView(viewName);
        log.info("[MV] 初始化开始：view={}, engine={}, engineConnectId={}, 源库={}:{}/{}{}, recreate={}",
                viewName, engine.engineType(), engineConnectId, originConnect.getDbHostnameIp(),
                originConnect.getDbPort(), database, materializedView == null ? "" : "（副本行已存在：" + materializedView.getId() + "）", recreate);

        // ========== 预检阶段（不执行任何 DDL，全部通过才进入执行阶段） ==========

        // P1. 视图展开：解析引用，MySQL 视图下钻到物理表
        MvViewExpander.Expansion expansion = expandOrigin(originConnect, database, originSql, strList(params, "tables"));
        String expandedSql = expansion.expandedSql();
        List<String> tables = expansion.physicalTables();
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("建表语句未解析到任何物理表（mysql-cdc 只能摄取物理表，MySQL 视图会被自动展开）");
        }
        log.info("[MV-Precheck] P1 视图展开：view={}, 展开的视图={}, 物理表={}, 耗时={}ms",
                viewName, expansion.expandedViews(), tables, System.currentTimeMillis() - startAt);
        if (!expandedSql.equals(originSql)) {
            log.info("[MV-Precheck] 展开后语句：view={}\n{}", viewName, expandedSql);
        }

        // P2. 计算目标 CDC 表名（不创建），改写 SQL
        String sourceName = sourceName(originConnect.getId(), database, viewName);
        Map<String, String> plannedMapping = new LinkedHashMap<>();
        for (String table : tables) {
            plannedMapping.put(table, (sourceName + "_" + table).toLowerCase(Locale.ROOT));
        }
        String engineSql = MvSqlRewriter.rewrite(expandedSql, plannedMapping, Set.of(database));
        if (engineSql.equals(expandedSql)) {
            throw new IllegalArgumentException(String.format(
                    "定义 SQL 未引用任何源表（%s），无法建立物化视图表名映射", tables));
        }
        log.info("[MV-Precheck] P2 SQL 改写完成，引擎执行语句：view={}\n{}", viewName, engineSql);

        // P2.5 构造建立物化视图的三步 DDL（只读内省源库，不执行任何 DDL）
        MaterializedViewEngine.SourceSpec spec = buildSpec(engineConnectId, originConnect, database, tables, viewName, snapshotEnabledOf(params));
        MaterializedViewEngine.DdlPlan plan = engine.buildDdlPlan(spec, viewName, engineSql);

        // P2.6 若前端回传了编辑后的 DDL，先逐段校验；不通过则不执行任何 DDL
        if (params.get("ddl") != null) {
            MaterializedViewEngine.DdlPlan edited = readEditedPlan(params, plan, plannedMapping);
            MaterializedViewEngine.DdlValidation validation = engine.validateDdl(spec, viewName, edited);
            if (!validation.passed()) {
                throw new IllegalStateException("DDL 校验未通过，未执行任何 DDL：\n"
                        + describeValidationFailure(validation));
            }
            plan = validation.normalized();
            log.info("[MV-Precheck] P2.6 编辑后 DDL 校验通过，按编辑后的 DDL 执行");
        }

        // P3. SELECT 语法预检：EXPLAIN 验证 RW 是否支持该 SQL（依赖 CDC 表已存在才能引用；
        //     若 CDC 表尚未创建，先跳过此步，在 CDC 表建好后再验证）
        boolean cdcTablesExist = plannedMapping.values().stream()
                .allMatch(t -> engine.relationExists(engineConnectId, t));
        if (cdcTablesExist) {
            try {
                engine.validateQuery(engineConnectId, plan.mvBody());
                log.info("[MV-Precheck] P3 SELECT 语法验证通过");
            } catch (Exception e) {
                throw new IllegalStateException("预检失败，不执行任何 DDL。请修正建表语句为 RW/PG 兼容语法后重试。\n" + e.getMessage(), e);
            }
        } else {
            log.info("[MV-Precheck] P3 跳过（CDC 表尚未创建，将在创建后验证 SELECT 语法）");
        }

        // P4. CDC 列类型预检：内省源表列，检查是否全部可映射到 RW 类型
        try (Connection originConn = originJdbc(originConnect, database)) {
            List<String> typeIssues = precheckColumnTypes(originConn, database, tables);
            if (!typeIssues.isEmpty()) {
                throw new IllegalStateException("预检失败，以下列类型 RW 不支持，请修改源表列类型后重试：\n" + String.join("\n", typeIssues));
            }
            log.info("[MV-Precheck] P4 CDC 列类型预检通过（{} 张表）", tables.size());
        } catch (SQLException e) {
            throw new IllegalStateException("连接源库预检列类型失败：" + e.getMessage(), e);
        }

        log.info("[MV-Precheck] 预检全部通过（{}ms），开始执行 DDL", System.currentTimeMillis() - startAt);

        // ========== 执行阶段 ==========

        long sourceStart = System.currentTimeMillis();
        MaterializedViewEngine.SourceTableMapping mapping = engine.applyDdl(spec, plan);
        log.info("[MV] 摄取通道就绪：view={}, sourceName={}, 表映射={}, 耗时={}ms",
                viewName, spec.sourceName(), mapping.asMap(), System.currentTimeMillis() - sourceStart);

        // CDC 表刚创建时补做 SELECT 语法验证（之前因表不存在跳过了）
        if (!cdcTablesExist) {
            try {
                engine.validateQuery(engineConnectId, plan.mvBody());
                log.info("[MV] CDC 表创建后 SELECT 语法验证通过");
            } catch (Exception e) {
                throw new IllegalStateException(
                        "CDC 摄取表已创建（数据在同步），但建表语句包含 RW 不支持的语法，物化视图未创建。"
                        + "请修正建表语句为 RW/PG 兼容语法后重新初始化。\n" + e.getMessage(), e);
            }
        }

        // P5 强制校验：物化视图最终输出列必须包含平台默认字段（MQL 租户隔离/数据权限过滤注入依赖这些列）。
        // CDC 摄取表此时已存在，用探测查询读取定义 SQL 的真实输出列；不通过则阻止物化视图创建。
        MaterializedViewEngine.CheckResult defaultColumns = defaultColumnCheck(engine, engineConnectId, plan.mvBody());
        log.info("[MV-Precheck] P5 平台默认字段校验：view={}, {} {}", viewName,
                defaultColumns.passed() ? "√" : "×", defaultColumns.message());
        if (!defaultColumns.passed()) {
            throw new IllegalStateException("物化视图创建被阻止：" + defaultColumns.message()
                    + "（物化视图未创建；CDC 摄取通道已保留可复用，修正定义 SQL 后重新初始化即可）");
        }

        boolean existed = engine.relationExists(engineConnectId, viewName);
        boolean reused = existed && !recreate;
        if (existed && recreate) {
            log.info("[MV] 重建：删除既有物化视图 view={}", viewName);
            engine.dropView(engineConnectId, null, viewName);
            existed = false;
        }
        if (!existed) {
            engine.applyMvDdl(spec, plan);
        }
        log.info("[MV] 物化视图{}：view={}, 耗时={}ms", reused ? "复用引擎内既有对象" : "创建完成",
                viewName, System.currentTimeMillis() - startAt);

        MaterializedRowSync sync = syncMaterializedRow(originView, materializedView, engine, engineConnect,
                engineConnectId, viewName, originSql, database, tables);
        // 初始化成功：把实际执行的（可能经前端编辑的）DDL JSON 持久化到副本行 viewConstruct
        String executedDdlJson = buildDdlStorageJson(engine, spec, tables, plannedMapping, plan, true);
        persistDdlToViewConstruct(engine, engineConnectId, viewName, executedDdlJson);
        String mode = sync.mode();

        MaterializedViewEngine.ViewStatus status = engine.viewStatus(engineConnectId, null, viewName);
        log.info("[MV] 初始化完成：view={}, 副本行={}, 状态={}, 总耗时={}ms",
                viewName, mode, status.state(), System.currentTimeMillis() - startAt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("viewType", ViewTypeEnum.MATERIALIZED.getCode());
        result.put("mode", mode);
        result.put("reused", reused);
        result.put("recreate", recreate);
        result.put("engineType", engine.engineType());
        result.put("engineConnectId", engineConnectId);
        result.put("sourceName", spec.sourceName());
        result.put("originSql", originSql);
        result.put("expandedSql", expandedSql);
        result.put("physicalTables", tables);
        result.put("ddlJson", executedDdlJson);
        if (!expansion.expandedViews().isEmpty()) {
            result.put("expandedViews", expansion.expandedViews());
        }
        result.put("engineSql", engineSql);
        result.put("ddl", ddlPayload(plan));
        result.put("columns", sync.columns().stream().map(MaterializedViewEngine.EngineColumn::name).toList());
        result.put("status", status);
        result.put("message", reused ? "复用引擎内既有物化视图（如需按新定义重建请传 recreate=true）"
                : "回填完成前（status=BACKFILLING）查询结果不完整，请以 /model/mv/view/status 确认 READY 后再承载查询");
        return result;
    }

    /**
     * 预览建立物化视图的三步 DDL（①建 CDC source ②建 CDC 摄取表 ③建物化视图），并逐段校验。
     * 只读内省源库，不连接引擎执行任何 DDL。
     */
    public Map<String, Object> previewDdl(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        DdlContext context = buildDdlContext(params);
        MaterializedViewEngine.DdlPlan plan = context.engine().buildDdlPlan(context.spec(), context.viewName(), context.engineSql());
        // 预览仅首存：副本行尚无引擎 DDL JSON 时落库（executed=false）；已有则跳过，
        // 避免打开面板自动预览就覆盖已执行/已保存的 DDL（用户视角即"每次打开都被重新生成"）
        TableView existingMvRow = findAnyMaterializedView(context.viewName());
        String existingConstruct = existingMvRow == null ? null : existingMvRow.getViewConstruct();
        if (existingConstruct == null || !existingConstruct.trim().startsWith("{")) {
            persistDdlToViewConstruct(context.engine(), context.spec().engineConnectId(), context.viewName(),
                    buildDdlStorageJson(context.engine(), context.spec(), context.physicalTables(),
                            context.plannedMapping(), plan, false));
        }
        MaterializedViewEngine.DdlValidation validation = withDefaultColumnCheck(context,
                context.engine().validateDdl(context.spec(), context.viewName(), plan));
        log.info("[MV-DDL] 预览完成：view={}, 结果={}, 共 {} 段, 耗时={}ms",
                context.viewName(), validation.passed() ? "通过" : "存在待修复项",
                validation.checks().size(), System.currentTimeMillis() - startAt);
        return ddlResult(context, plan, validation);
    }

    /**
     * 校验前端回传（可能已编辑）的三步 DDL，不执行任何 DDL。
     */
    public Map<String, Object> validateDdl(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        DdlContext context = buildDdlContext(params);
        MaterializedViewEngine.DdlPlan generated = context.engine().buildDdlPlan(context.spec(), context.viewName(), context.engineSql());
        MaterializedViewEngine.DdlPlan edited = readEditedPlan(params, generated, context.plannedMapping());
        // 校验仅首存：副本行尚无引擎 DDL JSON 时落库；已有（尤其 executed=true 的实际执行版）则跳过，避免覆盖
        TableView existingMvRow = findAnyMaterializedView(context.viewName());
        String existingConstruct = existingMvRow == null ? null : existingMvRow.getViewConstruct();
        if (existingConstruct == null || !existingConstruct.trim().startsWith("{")) {
            persistDdlToViewConstruct(context.engine(), context.spec().engineConnectId(), context.viewName(),
                    buildDdlStorageJson(context.engine(), context.spec(), context.physicalTables(),
                            context.plannedMapping(), edited, false));
        }
        MaterializedViewEngine.DdlValidation validation = withDefaultColumnCheck(context,
                context.engine().validateDdl(context.spec(), context.viewName(), edited));
        log.info("[MV-DDL] 校验完成：view={}, 结果={}, 耗时={}ms",
                context.viewName(), validation.passed() ? "通过" : "存在待修复项", System.currentTimeMillis() - startAt);
        return ddlResult(context, edited, validation);
    }

    /** 副本行同步结果：mode=created/updated，columns=物化视图列清单 */
    private record MaterializedRowSync(String mode, List<MaterializedViewEngine.EngineColumn> columns) {
    }

    /**
     * 引擎内物化视图就绪后：列内省 + 写入/更新 materialized 副本行 + 刷新元数据缓存。
     * <p>view/create 与 DDL 第③步单步执行共用，保证两条路径的元数据落库结果一致。</p>
     */
    private MaterializedRowSync syncMaterializedRow(TableView originView,
                                                    TableView materializedView,
                                                    MaterializedViewEngine engine,
                                                    ConnectMeta engineConnect,
                                                    String engineConnectId,
                                                    String viewName,
                                                    String originSql,
                                                    String database,
                                                    List<String> tables) {
        List<MaterializedViewEngine.EngineColumn> columns = engine.introspectColumns(engineConnectId, null, viewName);
        if (columns.isEmpty()) {
            throw new IllegalStateException(String.format("物化视图 %s 创建后未内省到任何列", viewName));
        }
        // 兜底校验（创建前探测门禁理论上已拦截）：最终列缺少平台默认字段时不写入副本行，阻止坏视图注册进元数据
        List<String> missingColumns = missingRequiredColumns(columns.stream().map(MaterializedViewEngine.EngineColumn::name).toList());
        if (!missingColumns.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "物化视图 %s 输出列缺少平台默认字段 %s（MQL 无法做租户隔离/数据权限过滤注入）；"
                            + "请删除引擎内该视图并修正定义 SQL 后重新初始化（本次未写入平台副本行）", viewName, missingColumns));
        }
        log.info("[MV] 列内省完成：view={}, 共 {} 列：{}", viewName, columns.size(),
                columns.stream().map(c -> c.name() + ":" + c.typeName()).toList());

        String mode;
        if (materializedView == null) {
            materializedView = new TableView();
            materializedView.setAppId(originView.getAppId());
            materializedView.setTenantCode(originView.getTenantCode());
            materializedView.setEntityName(originView.getEntityName());
            materializedView.setTitle(originView.getTitle());
            materializedView.setSeqNo(originView.getSeqNo());
            mode = "created";
        } else {
            mode = "updated";
        }
        materializedView.setConnectId(engineConnectId);
        materializedView.setViewName(viewName);
        materializedView.setViewType(ViewTypeEnum.MATERIALIZED.getCode());
        // viewConstruct 由 persistDdlToViewConstruct 写入引擎 DDL JSON；定义 SQL 与列元数据（viewColumn）以同名原视图行为准，
        // 原样继承，保证物化视图实体的字段元数据与原视图完全一致（含标题、引用/字典配置等）
        materializedView.setViewColumn(originView.getViewColumn());
        materializedView.setDescription(String.format("物化视图（引擎 %s，源 %s.%s，原视图 %s）",
                engine.engineType(), database, tables, viewName));
        materializedView.setEnableStatus(EnableStatusEnum.ENABLED.getValue());
        materializedView.setLinked(1);
        materializedView.afterSet();
        if ("created".equals(mode)) {
            devViewService.createModel(materializedView);
        } else {
            devViewService.updateModel(materializedView);
        }
        refreshMetaAndCache(viewName);
        return new MaterializedRowSync(mode, columns);
    }

    /**
     * 单步执行：只执行三步 DDL 中的某一步（step.key = source / cdc:&lt;originTable&gt; / mv），其余步骤不动。
     * <p>执行前按前端回传（可能已编辑）的 DDL 重新校验；校验不通过不阻断，仍按当前 DDL 执行并回传校验结论。
     * 第③步执行成功后同步平台侧 materialized 副本行与元数据（与 view/create 一致）。</p>
     */
    public Map<String, Object> executeStep(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        String stepKey = str(params, "stepKey");
        if (StringUtils.isBlank(stepKey)) {
            throw new IllegalArgumentException("stepKey 不能为空（source / cdc:<源表名> / mv）");
        }
        DdlContext context = buildDdlContext(params);
        MaterializedViewEngine.DdlPlan generated = context.engine().buildDdlPlan(context.spec(), context.viewName(), context.engineSql());
        MaterializedViewEngine.DdlPlan edited = readEditedPlan(params, generated, context.plannedMapping());
        MaterializedViewEngine.DdlValidation validation = withDefaultColumnCheck(context,
                context.engine().validateDdl(context.spec(), context.viewName(), edited));
        MaterializedViewEngine.StepCheck check = validation.checkOf(stepKey);
        if (check == null) {
            throw new IllegalArgumentException("未找到步骤 " + stepKey + "，可用步骤："
                    + validation.checks().stream().map(MaterializedViewEngine.StepCheck::key).toList());
        }
        // 校验未通过的步骤禁止执行：标识符/绑定等结构性错误会在引擎中创建后端无法追踪与清理的对象；
        // source-database-consistency 为提示性检查项（以 DDL 为准，只要求与第②步库名一致），不阻断
        List<MaterializedViewEngine.CheckResult> blocking = check.items().stream()
                .filter(item -> !item.passed() && !"source-database-consistency".equals(item.item()))
                .toList();
        if (!blocking.isEmpty()) {
            throw new IllegalArgumentException("步骤 " + stepKey + " 校验未通过，已禁止执行，请修复失败项后重新校验"
                    + "（或点击重新生成 DDL 还原原文）：" + blocking.stream()
                    .map(item -> item.item() + " — " + item.message())
                    .collect(Collectors.joining("；")));
        }
        MaterializedViewEngine.StepResult stepResult = context.engine().applyStep(context.spec(), edited, stepKey);
        log.info("[MV-DDL] 单步执行完成：view={}, step={}, 新建={}, 耗时={}ms",
                context.viewName(), stepKey, !stepResult.reused(), System.currentTimeMillis() - startAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", context.viewName());
        result.put("engineType", context.engine().engineType());
        result.put("stepKey", stepKey);
        result.put("title", stepTitle(stepKey, context.viewName(), edited));
        result.put("executed", stepResult.executed());
        result.put("reused", stepResult.reused());
        result.put("message", stepResult.message());
        result.put("passed", check.passed());
        result.put("items", check.items());
        result.put("detail", stepResult.detail());
        // 第③步：物化视图就绪后同步平台侧副本行与元数据
        if ("mv".equals(stepKey) && stepResult.executed()) {
            TableView originView = requireOriginView(context.viewName());
            TableView materializedView = findMaterializedView(context.viewName());
            MaterializedRowSync sync = syncMaterializedRow(originView, materializedView, context.engine(),
                    context.engineConnect(), context.spec().engineConnectId(), context.viewName(), context.originSql(),
                    context.database(), context.physicalTables());
            // mv 步执行成功：把实际执行的（可能经前端编辑的）DDL JSON 持久化到副本行 viewConstruct
            String executedDdlJson = buildDdlStorageJson(context.engine(), context.spec(), context.physicalTables(),
                    context.plannedMapping(), edited, true);
            persistDdlToViewConstruct(context.engine(), context.spec().engineConnectId(), context.viewName(), executedDdlJson);
            result.put("materialized", true);
            result.put("mode", sync.mode());
            result.put("columns", sync.columns().stream().map(MaterializedViewEngine.EngineColumn::name).toList());
            result.put("ddlJson", executedDdlJson);
        }
        return result;
    }

    /** DDL 预览/校验上下文：与 createView 的 P1/P2 完全同源 */
    private record DdlContext(MaterializedViewEngine engine,
                              ConnectMeta engineConnect,
                              ConnectMeta originConnect,
                              String database,
                              String viewName,
                              String originSql,
                              String expandedSql,
                              List<String> expandedViews,
                              List<String> physicalTables,
                              Map<String, String> plannedMapping,
                              String engineSql,
                              MaterializedViewEngine.SourceSpec spec) {
    }

    private DdlContext buildDdlContext(Map<String, Object> params) {
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        String viewName = normalizeViewName(str(params, "viewName"));
        ConnectMeta originConnect = requireConnect(str(params, "originConnectId"), "originConnectId");
        String database = resolveDatabase(params, originConnect);
        String originSql = requireOriginSql(params, viewName);
        MvViewExpander.Expansion expansion = expandOrigin(originConnect, database, originSql, strList(params, "tables"));
        List<String> tables = expansion.physicalTables();
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("建表语句未解析到任何物理表（mysql-cdc 只能摄取物理表，MySQL 视图会被自动展开）");
        }
        String sourceName = sourceName(originConnect.getId(), database, viewName);
        Map<String, String> plannedMapping = new LinkedHashMap<>();
        for (String table : tables) {
            plannedMapping.put(table, (sourceName + "_" + table).toLowerCase(Locale.ROOT));
        }
        String engineSql = MvSqlRewriter.rewrite(expansion.expandedSql(), plannedMapping, Set.of(database));
        MaterializedViewEngine.SourceSpec spec = buildSpec(engineConnectId, originConnect, database, tables, viewName, snapshotEnabledOf(params));
        return new DdlContext(engine, engineConnect, originConnect, database, viewName, originSql, expansion.expandedSql(),
                expansion.expandedViews(), tables, plannedMapping, engineSql, spec);
    }

    /** 解析前端回传的 DDL 载荷，并与当前源表清单核对 */
    private MaterializedViewEngine.DdlPlan readEditedPlan(Map<String, Object> params,
                                                          MaterializedViewEngine.DdlPlan generated,
                                                          Map<String, String> plannedMapping) {
        Map<String, Object> ddl = toMap(params.get("ddl"));
        if (ddl.isEmpty()) {
            return generated;
        }
        String sourceDdl = str(ddl, "sourceDdl");
        if (StringUtils.isBlank(sourceDdl)) {
            sourceDdl = generated.sourceDdl();
        }
        List<MaterializedViewEngine.CdcTableDdl> cdcTables = new ArrayList<>();
        Object cdcRaw = ddl.get("cdcTables");
        if (cdcRaw instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> cdc = toMap(item);
                cdcTables.add(new MaterializedViewEngine.CdcTableDdl(
                        str(cdc, "originTable"), str(cdc, "engineTable"), str(cdc, "ddl")));
            }
        }
        if (cdcTables.size() != plannedMapping.size()) {
            throw new IllegalArgumentException(String.format(
                    "源表清单已变更（当前 %d 张，前端回传 %d 张），请重新生成 DDL", plannedMapping.size(), cdcTables.size()));
        }
        for (MaterializedViewEngine.CdcTableDdl cdc : cdcTables) {
            String expected = null;
            for (Map.Entry<String, String> entry : plannedMapping.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cdc.originTable())) {
                    expected = entry.getValue();
                    break;
                }
            }
            if (expected == null) {
                throw new IllegalArgumentException("源表清单已变更（未知源表 " + cdc.originTable() + "），请重新生成 DDL");
            }
            if (expected.equalsIgnoreCase(cdc.engineTable())) {
                continue;
            }
            throw new IllegalArgumentException(String.format(
                    "CDC 摄取表标识符不可修改（%s 应为 %s），请重新生成 DDL", cdc.originTable(), expected));
        }
        String mvDdl = str(ddl, "mvDdl");
        if (StringUtils.isBlank(mvDdl)) {
            mvDdl = generated.mvDdl();
        }
        return new MaterializedViewEngine.DdlPlan(sourceDdl, cdcTables, mvDdl, generated.mvBody());
    }

    /** 统一的 DDL 响应结构（preview 与 validate 同构） */
    private Map<String, Object> ddlResult(DdlContext context,
                                          MaterializedViewEngine.DdlPlan plan,
                                          MaterializedViewEngine.DdlValidation validation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", context.viewName());
        result.put("engineType", context.engine().engineType());
        result.put("sourceName", context.spec().sourceName());
        result.put("expandedSql", context.expandedSql());
        result.put("engineSql", context.engineSql());
        result.put("physicalTables", context.physicalTables());
        result.put("expandedViews", context.expandedViews());
        result.put("passed", validation.passed());
        result.put("steps", ddlSteps(plan, validation, context.viewName()));
        return result;
    }

    /** 每段 DDL 的可编辑文本与逐项校验结论 */
    private List<Map<String, Object>> ddlSteps(MaterializedViewEngine.DdlPlan plan,
                                               MaterializedViewEngine.DdlValidation validation,
                                               String viewName) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (MaterializedViewEngine.StepCheck check : validation.checks()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("key", check.key());
            step.put("title", stepTitle(check.key(), viewName, plan));
            step.put("ddl", stepDdl(check.key(), plan));
            step.put("passed", check.passed());
            step.put("level", check.level());
            step.put("items", check.items());
            if (check.key().startsWith("cdc:")) {
                String originTable = check.key().substring("cdc:".length());
                MaterializedViewEngine.CdcTableDdl cdc = plan.cdcTableOf(originTable);
                step.put("originTable", originTable);
                step.put("engineTable", cdc == null ? null : cdc.engineTable());
            }
            steps.add(step);
        }
        return steps;
    }

    private String stepTitle(String key, String viewName, MaterializedViewEngine.DdlPlan plan) {
        if ("source".equals(key)) {
            return "1. 建立 CDC Source";
        }
        if ("mv".equals(key)) {
            return "3. 建立物化视图 " + viewName;
        }
        if (key.startsWith("cdc:")) {
            String originTable = key.substring("cdc:".length());
            MaterializedViewEngine.CdcTableDdl cdc = plan.cdcTableOf(originTable);
            return "2. 建立 CDC 摄取表 " + originTable
                    + (cdc == null ? "" : " → " + cdc.engineTable());
        }
        return key;
    }

    private String stepDdl(String key, MaterializedViewEngine.DdlPlan plan) {
        if ("source".equals(key)) {
            return plan.sourceDdl();
        }
        if ("mv".equals(key)) {
            return plan.mvDdl();
        }
        if (key.startsWith("cdc:")) {
            MaterializedViewEngine.CdcTableDdl cdc = plan.cdcTableOf(key.substring("cdc:".length()));
            return cdc == null ? "" : cdc.ddl();
        }
        return "";
    }

    /** DDL 载荷（前端回传给 view/create 时使用） */
    private Map<String, Object> ddlPayload(MaterializedViewEngine.DdlPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceDdl", plan.sourceDdl());
        List<Map<String, Object>> cdcTables = new ArrayList<>();
        for (MaterializedViewEngine.CdcTableDdl cdc : plan.cdcTables()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("originTable", cdc.originTable());
            item.put("engineTable", cdc.engineTable());
            item.put("ddl", cdc.ddl());
            cdcTables.add(item);
        }
        payload.put("cdcTables", cdcTables);
        payload.put("mvDdl", plan.mvDdl());
        return payload;
    }

    /**
     * 物化副本行 viewConstruct 存储载荷：三段引擎 DDL JSON（kind=mv-engine-ddl）。
     * <p>物化行不再用 viewConstruct 存定义 SQL（定义 SQL 以同名原视图行为准）；
     * 不冗余存 originSql/engineSql（前者在原视图行，后者包含在 mvDdl 内），控制存储体积。</p>
     */
    private String buildDdlStorageJson(MaterializedViewEngine engine,
                                       MaterializedViewEngine.SourceSpec spec,
                                       List<String> tables,
                                       Map<String, String> tableMapping,
                                       MaterializedViewEngine.DdlPlan plan,
                                       boolean executed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "mv-engine-ddl");
        payload.put("engineType", engine.engineType());
        payload.put("engineConnectId", spec.engineConnectId());
        payload.put("sourceName", spec.sourceName());
        payload.put("physicalTables", tables);
        payload.put("tableMapping", tableMapping);
        payload.put("snapshot", spec.snapshotEnabled());
        payload.put("executed", executed);
        payload.put("sourceDdl", plan.sourceDdl());
        List<Map<String, Object>> cdcTables = new ArrayList<>();
        for (MaterializedViewEngine.CdcTableDdl cdc : plan.cdcTables()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("originTable", cdc.originTable());
            item.put("engineTable", cdc.engineTable());
            item.put("ddl", cdc.ddl());
            cdcTables.add(item);
        }
        payload.put("cdcTables", cdcTables);
        payload.put("mvDdl", plan.mvDdl());
        return JSON.toJSONString(payload);
    }

    /** 把引擎 DDL JSON 持久化到物化副本行 viewConstruct；副本行不存在时按 enable 逻辑创建（预览阶段未启用也落库） */
    private void persistDdlToViewConstruct(MaterializedViewEngine engine,
                                           String engineConnectId,
                                           String viewName,
                                           String ddlJson) {
        TableView materializedView = findAnyMaterializedView(viewName);
        String mode;
        if (materializedView == null) {
            TableView originView = requireOriginView(viewName);
            materializedView = new TableView();
            materializedView.setAppId(originView.getAppId());
            materializedView.setTenantCode(originView.getTenantCode());
            materializedView.setEntityName(originView.getEntityName());
            materializedView.setTitle(originView.getTitle());
            materializedView.setSeqNo(originView.getSeqNo());
            materializedView.setLinked(0);
            mode = "created";
        } else {
            mode = "updated";
        }
        if ("created".equals(mode)) {
            materializedView.setDelStatus(cn.geelato.core.enums.DeleteStatusEnum.NO.getValue());
            materializedView.setDeleteAt(null);
            materializedView.setViewColumn("[]");
        }
        materializedView.setConnectId(engineConnectId);
        materializedView.setViewName(viewName);
        materializedView.setViewType(ViewTypeEnum.MATERIALIZED.getCode());
        materializedView.setViewConstruct(ddlJson);
        // 更新已有行时不动 viewColumn：保留 syncMaterializedRow 同步的列元数据（清空会导致实体查询报过滤字段不存在）
        materializedView.setEnableStatus(EnableStatusEnum.ENABLED.getValue());
        materializedView.afterSet();
        if ("created".equals(mode)) {
            devViewService.createModel(materializedView);
        } else {
            devViewService.updateModel(materializedView);
        }
        refreshMetaAndCache(viewName);
        log.info("[MV-DDL] 引擎 DDL 已落库：view={}, engine={}, 副本行={}, id={}",
                viewName, engine.engineType(), mode, materializedView.getId());
    }

    /** 读取并校验 originSql：为空或误传物化行存储的引擎 DDL JSON 时，回退为同名原视图行的定义 SQL */
    private String requireOriginSql(Map<String, Object> params, String viewName) {
        String originSql = str(params, "originSql");
        if (StringUtils.isNotBlank(originSql)) {
            String trimmed = originSql.trim();
            if (!trimmed.startsWith("{") && !trimmed.contains("\"kind\"")) {
                return originSql;
            }
        }
        // 前端兼容：物化行 view_construct 已改存引擎 DDL JSON，误传时回退为原视图行定义 SQL
        TableView originView = requireOriginView(viewName);
        String fallback = originView.getViewConstruct();
        if (StringUtils.isBlank(fallback)) {
            throw new IllegalArgumentException("originSql 不能为空（以源库表名书写的 SELECT 定义），且原视图定义 SQL 为空");
        }
        return fallback;
    }

    /** 校验不通过时按「哪一步 / 哪一项 / 怎么改」列出 */
    private String describeValidationFailure(MaterializedViewEngine.DdlValidation validation) {
        StringBuilder builder = new StringBuilder();
        for (MaterializedViewEngine.StepCheck check : validation.checks()) {
            List<MaterializedViewEngine.CheckResult> failed = check.items().stream()
                    .filter(item -> !item.passed()).toList();
            if (failed.isEmpty()) {
                continue;
            }
            builder.append("[").append(check.key()).append("]\n");
            for (MaterializedViewEngine.CheckResult item : failed) {
                builder.append("  - ").append(item.item()).append("：").append(item.message()).append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * 平台默认字段（column_default.json）列名集合，小写、去重；唯一事实来源为 MetaManager 默认列。
     */
    static Set<String> requiredColumnNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<ColumnMeta> defaults = MetaManager.singleInstance().getDefaultColumn();
        if (defaults != null) {
            for (ColumnMeta meta : defaults) {
                if (StringUtils.isNotBlank(meta.getName())) {
                    names.add(meta.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }

    /**
     * 计算实际列名清单中缺失的平台默认字段（大小写不敏感）。
     */
    static List<String> missingRequiredColumns(Collection<String> actualColumns) {
        Set<String> actual = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (actualColumns != null) {
            for (String name : actualColumns) {
                if (StringUtils.isNotBlank(name)) {
                    actual.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String required : requiredColumnNames()) {
            if (!actual.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    /**
     * 平台默认字段强制校验：用探测查询（SELECT * FROM (mvBody) LIMIT 0）读取定义 SQL 的真实输出列，
     * 与必含字段集比对。需要 CDC 摄取表已存在；探测失败视为不通过。
     */
    private MaterializedViewEngine.CheckResult defaultColumnCheck(MaterializedViewEngine engine,
                                                                  String engineConnectId,
                                                                  String mvBody) {
        List<String> actual;
        try {
            actual = engine.probeOutputColumns(engineConnectId, mvBody);
        } catch (Exception e) {
            return new MaterializedViewEngine.CheckResult(DEFAULT_COLUMN_CHECK_ITEM, false,
                    "无法探测物化视图输出列：" + e.getMessage());
        }
        List<String> missing = missingRequiredColumns(actual);
        if (missing.isEmpty()) {
            return new MaterializedViewEngine.CheckResult(DEFAULT_COLUMN_CHECK_ITEM, true,
                    String.format("物化视图输出列包含全部 %d 项平台默认字段（id、tenant_code、creator 等）",
                            requiredColumnNames().size()));
        }
        return new MaterializedViewEngine.CheckResult(DEFAULT_COLUMN_CHECK_ITEM, false,
                "物化视图输出列缺少平台默认字段 " + missing
                        + "；MQL 无法对该视图做租户隔离/数据权限过滤注入，请调整定义 SELECT 的输出列"
                        + "（建议直接输出包含默认字段的列，或使用 SELECT *）");
    }

    /**
     * 把平台默认字段校验项并入 DDL 分段校验结果（key=mv 的 StepCheck 追加 CheckResult，passed 重算）。
     * CDC 摄取表尚未创建时探测不可行，追加非阻断提示项（第③步执行前会强制校验）。
     */
    private MaterializedViewEngine.DdlValidation withDefaultColumnCheck(DdlContext context,
                                                                        MaterializedViewEngine.DdlValidation validation) {
        boolean cdcReady = false;
        try {
            cdcReady = !context.plannedMapping().isEmpty() && context.plannedMapping().values().stream()
                    .allMatch(t -> context.engine().relationExists(context.spec().engineConnectId(), t));
        } catch (Exception e) {
            log.debug("[MV-DDL] 检查 CDC 表是否存在失败，默认字段校验推迟：{}", e.getMessage());
        }
        MaterializedViewEngine.CheckResult item = cdcReady
                ? defaultColumnCheck(context.engine(), context.spec().engineConnectId(), validation.normalized().mvBody())
                : new MaterializedViewEngine.CheckResult(DEFAULT_COLUMN_CHECK_ITEM, true,
                "CDC 摄取表尚未创建，默认字段校验将在第③步（mv）执行前强制进行");
        List<MaterializedViewEngine.StepCheck> checks = new ArrayList<>();
        boolean passed = validation.passed();
        for (MaterializedViewEngine.StepCheck check : validation.checks()) {
            if ("mv".equals(check.key())) {
                List<MaterializedViewEngine.CheckResult> items = new ArrayList<>(check.items());
                items.add(item);
                boolean stepPassed = items.stream().allMatch(MaterializedViewEngine.CheckResult::passed);
                checks.add(new MaterializedViewEngine.StepCheck(check.key(), stepPassed, check.level(), items));
                if (!stepPassed) {
                    passed = false;
                }
            } else {
                checks.add(check);
            }
        }
        return new MaterializedViewEngine.DdlValidation(passed, validation.normalized(), checks);
    }

    private Map<String, Object> toMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return JSON.parseObject(text);
        }
        return result;
    }

    public Map<String, Object> viewStatus(Map<String, Object> params) {
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        String viewName = normalizeViewName(str(params, "viewName"));
        MaterializedViewEngine.ViewStatus status = engine.viewStatus(engineConnectId, null, viewName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engineType", engine.engineType());
        result.put("viewName", viewName);
        result.put("enabled", findMaterializedView(viewName) != null);
        result.put("status", status);
        return result;
    }

    public Map<String, Object> checkQuery(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        String viewName = normalizeViewName(str(params, "viewName"));
        if (!engine.relationExists(engineConnectId, viewName)) {
            throw new IllegalArgumentException(String.format("引擎 %s 中不存在物化视图 %s，请先初始化",
                    engine.engineType(), viewName));
        }
        DataSource ds = PlatformDataSources.getDataSource(engineConnectId);
        long total = 0;
        List<String> columnNames = new ArrayList<>();
        List<Map<String, Object>> sampleRow = new ArrayList<>();
        String sql = "SELECT * FROM " + viewName + " LIMIT 1";
        try (Connection conn = ds.getConnection();
             var st = conn.createStatement();
             var rs = st.executeQuery(sql)) {
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columnNames.add(meta.getColumnLabel(i));
            }
            if (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                sampleRow.add(row);
            }
            try (var countRs = st.executeQuery("SELECT count(*) FROM " + viewName)) {
                if (countRs.next()) {
                    total = countRs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("查询物化视图 %s 失败：%s",
                    viewName, e.getMessage()), e);
        }
        MaterializedViewEngine.ViewStatus status = engine.viewStatus(engineConnectId, null, viewName);
        log.info("[MV] 检查查询完成：view={}, 行数={}, 列数={}, 状态={}, 耗时={}ms",
                viewName, total, columnNames.size(), status.state(), System.currentTimeMillis() - startAt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("queryOk", true);
        result.put("totalRows", total);
        result.put("columns", columnNames);
        result.put("sampleRow", sampleRow.isEmpty() ? null : sampleRow.get(0));
        result.put("status", status);
        return result;
    }

    public Map<String, Object> disable(Map<String, Object> params) {
        String viewName = normalizeViewName(str(params, "viewName"));
        TableView materializedView = requireMaterializedView(viewName);
        materializedView.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        // 通用软删；DevViewService 特化版的 MySQL DDL 在引擎连接上不可执行
        this.isDeleteModel(materializedView);
        refreshMetaAndCache(viewName);
        log.info("[MV] 停用：view={}（副本行已软删，查询回原库视图；引擎物化视图保留）", viewName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("enabled", false);
        result.put("note", "已停用：查询恢复原库视图；引擎内物化视图保留，再次启用可复用（免回填）");
        return result;
    }

    public Map<String, Object> drop(Map<String, Object> params) {
        String viewName = normalizeViewName(str(params, "viewName"));
        TableView materializedView = requireMaterializedView(viewName);
        ConnectMeta engineConnect = requireConnect(materializedView.getConnectId(), "view.connectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        engine.dropView(materializedView.getConnectId(), null, viewName);
        boolean dropSource = Boolean.parseBoolean(String.valueOf(params.getOrDefault("dropSource", "false")));
        if (dropSource) {
            ConnectMeta originConnect = requireConnect(str(params, "originConnectId"), "originConnectId");
            String database = resolveDatabase(params, originConnect);
            engine.dropSource(materializedView.getConnectId(), sourceName(originConnect.getId(), database, viewName));
        }
        materializedView.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        this.isDeleteModel(materializedView);
        refreshMetaAndCache(viewName);
        log.info("[MV] 删除：view={}, dropSource={}, engineConnectId={}", viewName, dropSource, materializedView.getConnectId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("droppedView", viewName);
        result.put("dropSource", dropSource);
        return result;
    }

    public Map<String, Object> refreshMeta(Map<String, Object> params) {
        String viewName = normalizeViewName(str(params, "viewName"));
        // 兜底：副本行列元数据缺失（历史操作被清成 [] / 初始化未成功过）但引擎内视图已存在时，重新内省列并回写
        int syncedColumns = syncColumnsIfMissing(viewName);
        refreshMetaAndCache(viewName);
        log.info("[MV] 手动刷新元数据：view={}（元数据已重载，数据源路由缓存已清空，补同步列={}）", viewName, syncedColumns);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("refreshed", true);
        result.put("syncedColumns", syncedColumns);
        return result;
    }

    /**
     * 物化副本行 viewColumn 缺失时（空 / "[]"），且引擎内物化视图已存在，则内省列并回写副本行。
     * 用于修复历史脏数据（viewColumn 被清空）而无需重新初始化；返回内省到的列数（未触发返回 -1）。
     */
    private int syncColumnsIfMissing(String viewName) {
        TableView materializedView = findAnyMaterializedView(viewName);
        if (materializedView == null) {
            return -1;
        }
        String viewColumn = materializedView.getViewColumn();
        if (viewColumn != null && !viewColumn.isBlank() && !"[]".equals(viewColumn.trim())) {
            return -1;
        }
        String engineConnectId = materializedView.getConnectId();
        if (StringUtils.isBlank(engineConnectId)) {
            return -1;
        }
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        if (!engine.relationExists(engineConnectId, viewName)) {
            return -1;
        }
        List<MaterializedViewEngine.EngineColumn> columns = engine.introspectColumns(engineConnectId, null, viewName);
        if (columns.isEmpty()) {
            return -1;
        }
        List<String> missingColumns = missingRequiredColumns(columns.stream().map(MaterializedViewEngine.EngineColumn::name).toList());
        if (!missingColumns.isEmpty()) {
            log.warn("[MV] 列元数据补同步跳过：view={} 缺少平台默认字段 {}（请修正定义 SQL 后重新初始化）", viewName, missingColumns);
            return -1;
        }
        // 列元数据与原视图行保持一致：原样继承原视图 viewColumn（含标题、引用/字典配置等）
        materializedView.setViewColumn(requireOriginView(viewName).getViewColumn());
        devViewService.updateModel(materializedView);
        log.info("[MV] 列元数据补同步完成：view={}, 共 {} 列", viewName, columns.size());
        return columns.size();
    }

    // ======================== 数据对比 ========================

    /** 逐表行数对比 + 主键抽样对比（前/中/后各 1 行），大表 COUNT 耗时较长，避免频繁调用 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> compareData(Map<String, Object> params) {
        long startAt = System.currentTimeMillis();
        String engineConnectId = str(params, "engineConnectId");
        ConnectMeta engineConnect = requireConnect(engineConnectId, "engineConnectId");
        MaterializedViewEngine engine = resolveEngine(engineConnect);
        ConnectMeta originConnect = requireConnect(str(params, "originConnectId"), "originConnectId");
        String database = resolveDatabase(params, originConnect);
        String viewName = normalizeViewName(str(params, "viewName"));

        MvViewExpander.Expansion expansion = expandOrigin(originConnect, database, str(params, "originSql"), strList(params, "tables"));
        List<String> tables = expansion.physicalTables();
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("未解析到任何物理表，无法对比");
        }

        log.info("[MV-Compare] 数据对比开始：view={}, 物理表={}, 引擎={}", viewName, tables, engine.engineType());
        String sourceName = sourceName(originConnect.getId(), database, viewName);

        List<Map<String, Object>> results = new ArrayList<>();
        boolean allMatch = true;

        try (Connection originConn = originJdbc(originConnect, database)) {
            DataSource engineDs = PlatformDataSources.getDataSource(engineConnectId);
            for (String table : tables) {
                Map<String, Object> tableResult = new LinkedHashMap<>();
                tableResult.put("table", table);
                long tableStart = System.currentTimeMillis();

                long mysqlCount = queryCount(originConn, table);
                String cdcTable = (sourceName + "_" + table).toLowerCase(Locale.ROOT);
                long rwCount;
                try (Connection rwConn = engineDs.getConnection()) {
                    rwCount = queryCount(rwConn, cdcTable);
                }
                tableResult.put("mysqlCount", mysqlCount);
                tableResult.put("rwCount", rwCount);
                boolean countMatch = mysqlCount == rwCount;
                tableResult.put("countMatch", countMatch);
                if (!countMatch) {
                    allMatch = false;
                }
                log.info("[MV-Compare]   {}.{} MySQL={} 行, RW={} 行, {} 耗时={}ms",
                        database, table, mysqlCount, rwCount, countMatch ? "一致" : "不一致", System.currentTimeMillis() - tableStart);

                if (countMatch && mysqlCount > 0) {
                    List<String> pkColumns = getPrimaryKey(originConn, database, table);
                    if (!pkColumns.isEmpty()) {
                        List<Map<String, Object>> mismatches = sampleAndCompare(originConn, engineDs, table, cdcTable, pkColumns, mysqlCount);
                        tableResult.put("sampleMismatches", mismatches);
                        tableResult.put("sampleMatch", mismatches.isEmpty());
                        if (!mismatches.isEmpty()) {
                            allMatch = false;
                            log.warn("[MV-Compare]   {}.{} 抽样不一致 {} 条", database, table, mismatches.size());
                        }
                    }
                }

                tableResult.put("elapsedMs", System.currentTimeMillis() - tableStart);
                results.add(tableResult);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("数据对比连接失败：" + e.getMessage(), e);
        }

        log.info("[MV-Compare] 数据对比完成：view={}, 结果={}, 共 {} 表, 总耗时={}ms",
                viewName, allMatch ? "一致" : "存在差异", results.size(), System.currentTimeMillis() - startAt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", viewName);
        result.put("allMatch", allMatch);
        result.put("tables", results);
        result.put("totalElapsedMs", System.currentTimeMillis() - startAt);
        return result;
    }

    private static String idQuote(Connection conn) throws SQLException {
        return conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql") ? "`" : "\"";
    }

    private long queryCount(Connection conn, String table) throws SQLException {
        String q = idQuote(conn);
        try (var st = conn.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM " + q + table + q)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private List<String> getPrimaryKey(Connection conn, String database, String table) throws SQLException {
        List<String> pks = new ArrayList<>();
        try (var ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=? AND lower(table_name)=? AND constraint_name='PRIMARY' ORDER BY ordinal_position")) {
            ps.setString(1, database);
            ps.setString(2, table.toLowerCase(Locale.ROOT));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    pks.add(rs.getString(1));
                }
            }
        }
        return pks;
    }

    private List<Map<String, Object>> sampleAndCompare(Connection origin, DataSource engineDs,
                                                        String table, String cdcTable,
                                                        List<String> pkColumns, long totalRows) throws SQLException {
        List<Map<String, Object>> mismatches = new ArrayList<>();
        String pkCol = pkColumns.get(0);
        long[] offsets = totalRows <= 3 ? new long[]{0} : new long[]{0, totalRows / 2, totalRows - 1};
        for (long offset : offsets) {
            Map<String, Object> mysqlRow = queryRowByOffset(origin, table, pkCol, offset);
            if (mysqlRow == null) continue;
            Object pkValue = mysqlRow.get(pkCol.toLowerCase(Locale.ROOT));
            if (pkValue == null) continue;
            Map<String, Object> rwRow;
            try (Connection rwConn = engineDs.getConnection()) {
                rwRow = queryRowByPk(rwConn, cdcTable, pkCol, pkValue);
            }
            if (rwRow == null) {
                mismatches.add(Map.of("pk", String.valueOf(pkValue), "issue", "RW 侧缺失该行"));
                continue;
            }
            // 值归一化比较（不同 JDBC 驱动对同一时间戳/数值的字符串表示不同）
            for (Map.Entry<String, Object> entry : mysqlRow.entrySet()) {
                String col = entry.getKey();
                Object mysqlVal = entry.getValue();
                if (mysqlVal == null) continue;
                Object rwVal = rwRow.get(col);
                if (rwVal == null) {
                    mismatches.add(Map.of("pk", String.valueOf(pkValue), "column", col,
                            "mysql", String.valueOf(mysqlVal), "rw", "NULL"));
                } else if (!valueEquals(mysqlVal, rwVal)) {
                    mismatches.add(Map.of("pk", String.valueOf(pkValue), "column", col,
                            "mysql", String.valueOf(mysqlVal), "rw", String.valueOf(rwVal)));
                }
            }
        }
        return mismatches;
    }

    private boolean valueEquals(Object mysqlVal, Object rwVal) {
        // 时间/日期类型统一转为 epoch 毫秒比较，避免不同 JDBC 驱动返回类型不同（如 LocalDateTime vs Timestamp）
        Object a = toEpochMillis(mysqlVal);
        Object b = toEpochMillis(rwVal);
        if (a instanceof Long ta && b instanceof Long tb) {
            return ta.longValue() == tb.longValue();
        }
        if (a instanceof Number an && b instanceof Number bn) {
            return new java.math.BigDecimal(an.toString()).compareTo(new java.math.BigDecimal(bn.toString())) == 0;
        }
        if (a instanceof Boolean ab && b instanceof Boolean bb) {
            return ab.equals(bb);
        }
        // tinyint(1) 类列：MySQL 驱动返回 Boolean，RW 侧可能是数值
        if (a instanceof Boolean abn && b instanceof Number bnn) {
            return (abn ? 1 : 0) == bnn.intValue();
        }
        if (a instanceof Number ann && b instanceof Boolean bbn) {
            return ann.intValue() == (bbn ? 1 : 0);
        }
        return normalizeString(a).equals(normalizeString(b));
    }

    /** 将时间/日期类型统一转换为 epoch 毫秒（本地时区），非时间类型原样返回 */
    private Object toEpochMillis(Object v) {
        if (v instanceof java.sql.Timestamp ts) return ts.getTime();
        if (v instanceof java.util.Date d) return d.getTime();
        if (v instanceof LocalDateTime ldt) return java.sql.Timestamp.valueOf(ldt).getTime();
        if (v instanceof LocalDate ld) return java.sql.Timestamp.valueOf(ld.atStartOfDay()).getTime();
        if (v instanceof LocalTime lt) return lt.toSecondOfDay() * 1000L;
        if (v instanceof OffsetDateTime odt) return odt.toInstant().toEpochMilli();
        if (v instanceof ZonedDateTime zdt) return zdt.toInstant().toEpochMilli();
        if (v instanceof Instant i) return i.toEpochMilli();
        return v;
    }

    private String normalizeString(Object val) {
        String s = String.valueOf(val).trim();
        s = s.replace('T', ' ');
        s = s.replaceAll("\\.\\d+$", "");
        s = s.replaceAll("(\\d{2}:\\d{2})$", "$1:00");
        return s;
    }

    private Map<String, Object> queryRowByOffset(Connection conn, String table, String pkCol, long offset) throws SQLException {
        String q = idQuote(conn);
        String sql = String.format("SELECT * FROM %s%s%s ORDER BY %s%s%s LIMIT 1 OFFSET %d", q, table, q, q, pkCol, q, offset);
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return readRowAsMap(rs);
            }
        }
        return null;
    }

    private Map<String, Object> queryRowByPk(Connection conn, String cdcTable, String pkCol, Object pkValue) throws SQLException {
        String sql = String.format("SELECT * FROM \"%s\" WHERE \"%s\" = ?", cdcTable, pkCol.toLowerCase(Locale.ROOT));
        try (var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, pkValue);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readRowAsMap(rs);
                }
            }
        }
        return null;
    }

    private Map<String, Object> readRowAsMap(java.sql.ResultSet rs) throws SQLException {
        var meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
        }
        return row;
    }

    // ======================== 内部工具 ========================

    /** RW 不支持的 MySQL 列类型 */
    private static final Set<String> UNSUPPORTED_TYPES = Set.of("set", "geometry", "linestring", "polygon",
            "multipoint", "multilinestring", "multipolygon", "geometrycollection");

    /** CDC 列类型预检：检查源表所有列是否可映射到 RW（不支持的类型提前报错，不执行任何 DDL） */
    private List<String> precheckColumnTypes(Connection conn, String database, List<String> tables) throws SQLException {
        List<String> issues = new ArrayList<>();
        String sql = "SELECT table_name, column_name, column_type FROM information_schema.columns "
                + "WHERE table_schema = ? AND lower(table_name) IN ("
                + String.join(",", java.util.Collections.nCopies(tables.size(), "?")) + ")";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, database);
            int idx = 2;
            for (String t : tables) {
                ps.setString(idx++, t.toLowerCase(Locale.ROOT));
            }
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnType = rs.getString("column_type").toLowerCase(Locale.ROOT);
                    String baseType = columnType.split("[\\s(]")[0];
                    if (UNSUPPORTED_TYPES.contains(baseType)) {
                        issues.add(String.format("  %s.%s → %s（RW 不支持）",
                                rs.getString("table_name"), rs.getString("column_name"), columnType));
                    }
                }
            }
        }
        return issues;
    }

    private MvViewExpander.Expansion expandOrigin(ConnectMeta originConnect, String database, String sql, List<String> extraTables) {
        String url = String.format(
                "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=60000",
                originConnect.getDbHostnameIp(), originConnect.getDbPort(), database);
        try (Connection conn = DriverManager.getConnection(url,
                originConnect.getDbUserName(), EncryptUtils.decrypt(originConnect.getDbPassword()))) {
            return new MvViewExpander().expand(conn, database, sql, extraTables == null ? List.of() : extraTables);
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("连接源库解析视图引用失败（%s:%s/%s）：%s",
                    originConnect.getDbHostnameIp(), originConnect.getDbPort(), database, e.getMessage()), e);
        }
    }

    private MaterializedViewEngine.SourceSpec buildSpec(String engineConnectId, ConnectMeta originConnect,
                                                        String database, List<String> tables, String viewName,
                                                        boolean snapshotEnabled) {
        return new MaterializedViewEngine.SourceSpec(
                engineConnectId,
                originConnect.getId(),
                originConnect.getDbType(),
                originConnect.getDbHostnameIp(),
                originConnect.getDbPort(),
                database,
                originConnect.getDbUserName(),
                EncryptUtils.decrypt(originConnect.getDbPassword()),
                sourceName(originConnect.getId(), database, viewName),
                tables,
                snapshotEnabled);
    }

    /** snapshot 开关：true（默认）= CDC 摄取表建立时执行初始快照回填；false = 跳过快照，靠 binlog 追平，避免大表回填压力 */
    private boolean snapshotEnabledOf(Map<String, Object> params) {
        Object value = params.get("snapshot");
        if (value == null) {
            return true;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private MaterializedViewEngine findEngine(ConnectMeta connect) {
        for (MaterializedViewEngine engine : engines) {
            if (engine.supports(connect.getDbType())) {
                return engine;
            }
        }
        return null;
    }

    private MaterializedViewEngine resolveEngine(ConnectMeta connect) {
        MaterializedViewEngine engine = findEngine(connect);
        if (engine == null) {
            throw new IllegalStateException(String.format("连接 %s（db_type=%s）没有物化视图引擎适配器，已注册引擎：%s",
                    connect.getId(), connect.getDbType(), engineTypes()));
        }
        return engine;
    }

    private String engineTypes() {
        List<String> types = engines.stream().map(MaterializedViewEngine::engineType).toList();
        return types.isEmpty() ? "（无）" : types.toString();
    }

    private ConnectMeta requireConnect(String connectId, String fieldName) {
        if (StringUtils.isBlank(connectId)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        ConnectMeta connect = getModel(ConnectMeta.class, connectId);
        if (connect == null) {
            throw new IllegalArgumentException("连接不存在：" + fieldName + "=" + connectId);
        }
        return connect;
    }

    private List<TableView> queryViewsByName(String viewName) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("viewName", viewName);
        return devViewService.queryModel(TableView.class, filter);
    }

    private TableView requireOriginView(String viewName) {
        List<TableView> views = queryViewsByName(viewName);
        if (views != null) {
            for (TableView view : views) {
                if (!ViewTypeEnum.MATERIALIZED.getCode().equals(view.getViewType())) {
                    return view;
                }
            }
        }
        throw new IllegalArgumentException("原视图不存在（物化视图是既有视图的加速副本，请先创建 default/custom/virtual 视图）：" + viewName);
    }

    private TableView findMaterializedView(String viewName) {
        List<TableView> views = queryViewsByName(viewName);
        if (views != null) {
            for (TableView view : views) {
                if (ViewTypeEnum.MATERIALIZED.getCode().equals(view.getViewType())) {
                    return view;
                }
            }
        }
        return null;
    }

    /**
     * 含已软删行，再启用时恢复旧行而非新建。
     * <p>注意：dao 为全局单例，BaseService.queryModel 会把 delStatus 默认过滤置为常开，
     * 而 queryList 会合并该默认过滤（applyDefaultFilter），软删行会被过滤掉导致重复新增副本行；
     * 因此这里必须显式关闭默认过滤查询，查完在 finally 中恢复 BaseService 期望的状态。
     */
    @SuppressWarnings("unchecked")
    private TableView findAnyMaterializedView(String viewName) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("viewName", viewName);
        dao.setDefaultFilter(false, null);
        try {
            List<TableView> views = (List<TableView>) dao.queryList(TableView.class, filter, null);
            if (views != null) {
                for (TableView view : views) {
                    if (ViewTypeEnum.MATERIALIZED.getCode().equals(view.getViewType())) {
                        return view;
                    }
                }
            }
            return null;
        } finally {
            dao.setDefaultFilter(true, filterGroup);
        }
    }

    private TableView requireMaterializedView(String viewName) {
        TableView view = findMaterializedView(viewName);
        if (view == null) {
            throw new IllegalArgumentException("物化视图未启用（不存在 materialized 副本行）：" + viewName);
        }
        return view;
    }

    private List<String> requireTables(Map<String, Object> params) {
        List<String> tables = strList(params, "tables");
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("源表清单（tables）不能为空");
        }
        return tables;
    }

    private String resolveDatabase(Map<String, Object> params, ConnectMeta originConnect) {
        String database = str(params, "database");
        if (StringUtils.isNotBlank(database)) {
            return database;
        }
        if (StringUtils.isNotBlank(originConnect.getDbName())) {
            return originConnect.getDbName();
        }
        throw new IllegalArgumentException("database 不能为空（源连接未登记 db_name）");
    }

    /** 通道按视图隔离：gl_{连接id}_{库名}_{视图名}，超长截断加哈希后缀 */
    private String sourceName(String originConnectId, String database, String viewName) {
        String name = ("gl_" + IDENT_PART_PATTERN.matcher(originConnectId == null ? "" : originConnectId).replaceAll("_")
                + "_" + IDENT_PART_PATTERN.matcher(database == null ? "" : database).replaceAll("_")
                + "_" + IDENT_PART_PATTERN.matcher(viewName == null ? "" : viewName).replaceAll("_")).toLowerCase(Locale.ROOT);
        if (name.length() > 63) {
            String hash = Integer.toHexString((originConnectId + ":" + database + ":" + viewName).hashCode());
            name = name.substring(0, 63 - hash.length() - 1) + "_" + hash;
        }
        return name;
    }

    /** resolver 缓存永不过期，必须手动清 */
    private void refreshMetaAndCache(String viewName) {
        metaManager.refreshDBMeta(viewName);
        EntityDataSourceResolver.getInstance().clearCache();
    }

    private Connection originJdbc(ConnectMeta originConnect, String database) throws SQLException {
        String url = String.format(
                "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=120000",
                originConnect.getDbHostnameIp(), originConnect.getDbPort(), database);
        return DriverManager.getConnection(url, originConnect.getDbUserName(), EncryptUtils.decrypt(originConnect.getDbPassword()));
    }

    private String engineSchema(ConnectMeta engineConnect) {
        return StringUtils.isBlank(engineConnect.getDbSchema()) ? "public" : engineConnect.getDbSchema();
    }

    private String normalizeViewName(String viewName) {
        if (StringUtils.isBlank(viewName)) {
            throw new IllegalArgumentException("viewName 不能为空");
        }
        String normalized = viewName.toLowerCase(Locale.ROOT);
        if (!VIEW_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("viewName 仅允许小写字母开头的小写字母/数字/下划线（≤63 位）：" + viewName);
        }
        return normalized;
    }

    private String str(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private List<String> strList(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
