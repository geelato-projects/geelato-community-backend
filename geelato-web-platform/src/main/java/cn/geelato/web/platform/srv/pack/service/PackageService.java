package cn.geelato.web.platform.srv.pack.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.parser.JsonTextSaveParser;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.AppVersion;
import cn.geelato.meta.Attachment;
import cn.geelato.pack.PackageConfigurationProperties;
import cn.geelato.pack.PackageException;
import cn.geelato.pack.PlatformTableConstant;
import cn.geelato.pack.entity.AppMeta;
import cn.geelato.pack.entity.AppPackData;
import cn.geelato.pack.enums.PackageSourceEnum;
import cn.geelato.pack.enums.PackageStatusEnum;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import cn.geelato.web.platform.srv.base.service.UploadService;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.srv.file.param.FileParam;
import cn.geelato.web.platform.utils.FileParamUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 应用打包/部署服务（v2 演进版）。
 * <p>
 * 通过 {@code geelato.package.engine=v2} 启用。与 {@link cn.geelato.web.platform.srv.pack.PackageController}
 * 中保留的 v1 逻辑并存（绞杀者模式），v1 原地冻结，所有演进在此进行。
 * <p>
 * 相对 v1 的关键改进：
 * <ul>
 *   <li>无单例可变状态——所有部署上下文（增量表集合、已存在 id）均为方法局部变量，线程安全；</li>
 *   <li>部署采用声明式 {@code @Transactional}，事务边界清晰，失败自动回滚；</li>
 *   <li>部署前自动备份当前版本，支持回滚；</li>
 *   <li>清洗/过滤基于 {@link PlatformTableConstant#isPlatformTable(String)} 白名单，绕开 v1 的 AppMeta#getMetaType bug。</li>
 * </ul>
 *
 * @author diabl
 */
@Component
@Slf4j
public class PackageService extends BaseService {

    private static final String SAVE_TABLE_TYPE = AttachmentSourceEnum.ATTACH.getValue();
    private static final String PACKAGE_FILE_SUFFIX = ".gdp";
    private static final String COMPRESS_PACKAGE_FILE_SUFFIX = ".zgdp";
    private static final String DEFAULT_PACKAGE_NAME = "geelatoApp";
    /** 业务表名合法性校验：仅允许标准数据库标识符，防 SQL 注入。 */
    private static final Pattern VALID_TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    @Resource
    private PackageConfigurationProperties packageConfigurationProperties;
    @Resource
    private FileHandler fileHandler;
    @Lazy
    @jakarta.annotation.Resource
    private AppVersionService appVersionService;

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final JsonTextSaveParser jsonTextSaveParser = new JsonTextSaveParser();

    // ======================================================================
    // v2 打包
    // ======================================================================

    /**
     * 打包应用（v2）。
     *
     * @param appId        应用ID
     * @param version      版本名称，为空则自动生成
     * @param description  描述，为空给默认值
     * @param appointMetas 指定打包的行ID（metaName -> 逗号分隔的id），为空表示全部打包
     * @return 创建的应用版本
     */
    public AppVersion packetV2(String appId, String version, String description, Map<String, String> appointMetas) {
        long startTime = System.currentTimeMillis();
        log.info("====================== v2 pack start ======================");
        log.info("打包应用：appId={}, 指定元数据={}", appId, appointMetas == null ? "全部" : appointMetas.keySet());
        AppPackData appPackage = buildAppPackDataV2(appId, appointMetas);
        if (StringUtils.isEmpty(appPackage.getAppCode())) {
            log.warn("打包失败：找不到可打包的应用，appId={}", appId);
            throw new PackageException("找不到可打包的应用");
        }

        AppVersion av = new AppVersion();
        av.setAppId(appId);
        String packageVersion = StringUtils.isEmpty(version) ? generateVersionCodeV2(appPackage.getAppCode()) : version;
        av.setVersion(packageVersion);
        appPackage.setVersion(packageVersion);
        av.setDescription(StringUtils.isEmpty(description) ? "当前环境打包形成的应用包" : description);
        av.setPackageSource(PackageSourceEnum.PACKET.getValue());
        av.setStatus(PackageStatusEnum.DRAFT.getValue());
        av.setPacketTime(new Date());
        av.setTenantCode(getSessionTenantCode());

        String filePath = writePackageDataV2(av, appPackage);
        av.setPackagePath(filePath);
        AppVersion created = createModel(av);
        log.info("打包完成：appCode={}, 版本={}, 基准平台版本={}, 元数据 {} 张, 包文件={}, 耗时 {} ms",
                appPackage.getAppCode(), packageVersion, appPackage.getBasePlatformVersion(),
                appPackage.getAppMetaList() == null ? 0 : appPackage.getAppMetaList().size(),
                filePath, System.currentTimeMillis() - startTime);
        return created;
    }

    /**
     * 合并多个版本打包（v2）。
     */
    public AppVersion packetMergeV2(String appId, String version, String description, Map<String, Map<String, String>> appointMetas) {
        long startTime = System.currentTimeMillis();
        String[] versionIds = appointMetas.keySet().toArray(new String[0]);
        log.info("====================== v2 pack merge start ======================");
        log.info("合并打包：appId={}, 版本ids={}", appId, Arrays.toString(versionIds));
        List<AppPackData> appPackages = getAppointAppPackageV2(versionIds);
        AppPackData appPackage = cn.geelato.pack.utils.PackageUtils.mergePackage(appPackages, appointMetas);

        AppVersion av = new AppVersion();
        av.setAppId(appId);
        av.setVersion(StringUtils.isEmpty(version) ? generateVersionCodeV2(appPackage.getAppCode()) : version);
        av.setDescription(StringUtils.isEmpty(description) ? "对比合并产生的包" : description);
        av.setPackageSource(PackageSourceEnum.PACKET.getValue());
        av.setStatus(PackageStatusEnum.DRAFT.getValue());
        av.setPacketTime(new Date());
        av.setTenantCode(getSessionTenantCode());

        String filePath = writePackageDataV2(av, appPackage);
        av.setPackagePath(filePath);
        AppVersion created = createModel(av);
        log.info("合并打包完成：appCode={}, 元数据 {} 张, 包文件={}, 耗时 {} ms",
                appPackage.getAppCode(),
                appPackage.getAppMetaList() == null ? 0 : appPackage.getAppMetaList().size(),
                filePath, System.currentTimeMillis() - startTime);
        return created;
    }

    // ======================================================================
    // v2 部署
    // ======================================================================

    /**
     * 部署版本包（v2）。
     * <p>
     * 编排：读包 → 校验 → 自动备份 → {@link #deployAppPackageDataV2}(声明式事务) → 更新版本状态 → 刷新缓存。
     *
     * @param versionId 应用版本ID
     * @return 操作结果
     */
    public ApiResult<?> deployV2(String versionId) {
        long startTime = System.currentTimeMillis();
        log.info("v2 应用部署开始：versionId={}", versionId);
        if ("init_source".equals(packageConfigurationProperties.getEnv())) {
            return ApiResult.fail("本环境无法部署任何应用，请联系管理员！");
        }

        AppVersion appVersion = appVersionService.getModel(AppVersion.class, versionId);
        if (appVersion == null || StringUtils.isEmpty(appVersion.getPackagePath())) {
            throw new PackageException("无法读取到应用版本信息，请检查应用版本");
        }
        log.info("v2 应用部署：appId={}, 包文件={}", appVersion.getAppId(), appVersion.getPackagePath());

        // 1. 读包（事务外）
        String appPackageData = readPackageDataV2(appVersion);
        AppPackData appPackage = cn.geelato.pack.utils.PackageUtils.resolveAppPackageData(appPackageData);
        if (appPackage == null || appPackage.getAppMetaList() == null || appPackage.getAppMetaList().isEmpty()) {
            throw new PackageException("无法读取到应用包数据，请检查应用包");
        }
        log.info("v2 应用包解析完成：appCode={}, 基准平台版本={}, 元数据 {} 张",
                appPackage.getAppCode(), appPackage.getBasePlatformVersion(), appPackage.getAppMetaList().size());

        // 2. 校验（事务外）
        try {
            if (!cn.geelato.pack.utils.PackageUtils.validatePackageData(appPackage, metaManager.getAll())) {
                throw new PackageException("应用包校验不通过,请先更新平台应用geelato_admin至版本" + appPackage.getBasePlatformVersion());
            }
        } catch (PackageException pe) {
            throw pe;
        } catch (Exception e) {
            log.error("v2 应用包校验异常：versionId={}, appId={}", versionId, appVersion.getAppId(), e);
            throw new PackageException("应用部署失败：" + withFieldMetaHint(rootMsg(e)));
        }

        // 3. 备份当前版本（独立提交，部署失败时备份仍在）。已是 backup 版本则跳过，避免递归。
        boolean isBackup = PackageStatusEnum.BACKUP.getValue().equals(appVersion.getStatus());
        if (!isBackup) {
            backupCurrentVersionV2(appVersion.getAppId());
        }

        // 4. 部署（声明式事务，失败自动回滚）
        deployAppPackageDataV2(appPackage);

        // 5. 更新版本状态
        if (!isBackup) {
            appVersion.setStatus(PackageStatusEnum.DEPLOYED.getValue());
            updateModel(appVersion);
        }

        // 6. 刷新缓存
        refreshAppV2(appVersion.getAppId());

        log.info("v2 应用部署成功：versionId={}, appId={}, 耗时 {} ms", versionId, appVersion.getAppId(), System.currentTimeMillis() - startTime);
        return ApiResult.success(null, "应用部署成功！");
    }

    /**
     * 回滚到最近一次备份版本（v2）。
     *
     * @param appId 应用ID
     * @return 操作结果
     */
    public ApiResult<?> rollbackV2(String appId) {
        Map<String, Object> params = new HashMap<>();
        params.put("appId", appId);
        params.put("status", PackageStatusEnum.BACKUP.getValue());
        params.put("delStatus", 0);
        List<AppVersion> backups = queryModel(AppVersion.class, params, "create_at DESC");
        if (backups == null || backups.isEmpty()) {
            return ApiResult.fail("未找到应用 " + appId + " 的备份版本，无法回滚");
        }
        AppVersion backupVersion = backups.get(0);
        // 回滚 = 用备份版本部署，备份版本部署时不再生成新备份，避免递归。
        return deployV2(backupVersion.getId());
    }

    // ======================================================================
    // v2 部署核心：声明式事务包裹 删除 + 插入
    // ======================================================================

    /**
     * 部署应用包数据（v2）。声明式事务，失败自动回滚。
     * <p>
     * 注意：增量表（字典、系统配置等）只插入目标库不存在的新行；其余表先删后插。
     *
     * @param appPackage 应用包
     */
    @Transactional(rollbackFor = Exception.class)
    public void deployAppPackageDataV2(AppPackData appPackage) {
        log.info("----------------------v2 deploy start--------------------");
        String sourceAppId = appPackage.getSourceAppId();
        DeployContext ctx = buildDeployContextV2(sourceAppId);
        deleteCurrentVersionV2(sourceAppId, ctx);
        insertPackageDataV2(appPackage, ctx);
        log.info("----------------------v2 deploy end--------------------");
    }

    // ======================================================================
    // v2 私有助手：打包
    // ======================================================================

    /**
     * 构建应用包数据（v2）。从平台表 + 标记 pack_bus_data 的业务表查询数据组装。
     */
    private AppPackData buildAppPackDataV2(String appId, Map<String, String> appointMetas) {
        Map<String, String> appDataMap = new LinkedHashMap<>();
        // 平台表（SQL 以 ? 占位，appId 作为参数）
        appDataMap.putAll(buildPlatformPackageSqlMapV2(appId));
        // 业务表
        appDataMap.putAll(buildBizDataSqlMapV2(appId, "package"));

        AppPackData appPackage = new AppPackData();
        List<AppMeta> appMetaList = new ArrayList<>();
        Map<String, List<String>> inconsistentTables = new LinkedHashMap<>();

        // 基础平台版本
        String basePlatformVersion = dao.getJdbcTemplate()
                .queryForMap("select version_info from platform_app where code='geelato_admin'")
                .get("version_info").toString();
        appPackage.setBasePlatformVersion(basePlatformVersion);

        for (Map.Entry<String, String> entry : appDataMap.entrySet()) {
            String metaName = entry.getKey();
            List<Map<String, Object>> metaData = dao.getJdbcTemplate().queryForList(entry.getValue(), appId);
            List<String> unknownColumns = findUnknownColumns(metaName, metaData);
            if (!unknownColumns.isEmpty()) {
                inconsistentTables.put(metaName, unknownColumns);
            }
            log.info("v2 打包表 [{}]：{} 行", metaName, metaData.size());
            if (PlatformTableConstant.APP.equals(metaName) && !metaData.isEmpty()) {
                appPackage.setAppCode(String.valueOf(metaData.get(0).get("code")));
                appPackage.setAppName(String.valueOf(metaData.get(0).get("name")));
                appPackage.setSourceAppId(appId);
            } else {
                if (appointMetas != null && appointMetas.containsKey(metaName)) {
                    List<Map<String, Object>> picked = cn.geelato.pack.utils.PackageUtils
                            .pickMetaData(metaData, appointMetas.get(metaName));
                    appMetaList.add(new AppMeta(metaName, picked));
                } else {
                    appMetaList.add(new AppMeta(metaName, metaData));
                }
            }
        }

        assertColumnsConsistent(inconsistentTables);

        appPackage.setAppMetaList(appMetaList);
        return appPackage;
    }

    /**
     * 打包前校验：返回物理表中存在、但实体定义中没有的列（select * 查询结果的列集合即物理列集合）。
     * <p>物理表与实体定义不同步时打出的应用包，部署校验时必然抛 unfound FieldMeta 失败，故打包前拦截。</p>
     *
     * @param metaName 实体名（平台表与表名一致）
     * @param metaData select * 查询结果
     * @return 多余的物理列名（排序）；实体未注册、无数据或无不一致时返回空列表
     */
    public List<String> findUnknownColumns(String metaName, List<Map<String, Object>> metaData) {
        if (metaData == null || metaData.isEmpty()) {
            return Collections.emptyList();
        }
        EntityMeta entityMeta = metaManager.getByEntityName(metaName);
        if (entityMeta == null || entityMeta.getFieldMetas() == null || entityMeta.getFieldMetas().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> knownColumns = new HashSet<>();
        for (FieldMeta fm : entityMeta.getFieldMetas()) {
            knownColumns.add(fm.getColumnName());
        }
        Set<String> unknownColumns = new TreeSet<>();
        for (Map<String, Object> row : metaData) {
            for (String col : row.keySet()) {
                if (!knownColumns.contains(col)) {
                    unknownColumns.add(col);
                }
            }
        }
        return new ArrayList<>(unknownColumns);
    }

    /**
     * 打包前校验：存在物理表与实体定义不一致的表时抛出异常，阻止打包。
     *
     * @param inconsistentTables key:实体名，value:该表物理列中实体定义没有的字段
     */
    public void assertColumnsConsistent(Map<String, List<String>> inconsistentTables) {
        if (inconsistentTables == null || inconsistentTables.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder("打包校验失败：物理表与实体定义不一致，禁止打包。");
        for (Map.Entry<String, List<String>> entry : inconsistentTables.entrySet()) {
            message.append("实体 [").append(entry.getKey()).append("] 的物理表中存在实体定义没有的字段：")
                    .append(entry.getValue()).append("；");
        }
        message.append("请确保两侧一致。");
        log.error(message.toString());
        throw new PackageException(message.toString());
    }

    /** 部署报错增强：unfound FieldMeta 时追加原因说明，便于定位版本/结构不一致问题。 */
    public static String withFieldMetaHint(String message) {
        if (message != null && message.contains("unfound FieldMeta")) {
            return message + "（该字段在目标平台实体定义中不存在，通常为源/目标平台版本不一致，或源环境物理表与实体定义不同步所致）";
        }
        return message;
    }

    private static String rootMsg(Throwable ex) {
        return StringUtils.isEmpty(ex.getMessage()) ? ex.toString() : ex.getMessage();
    }

    /**
     * 构建平台表打包查询SQL映射（v2）：SQL 含 ? 占位，调用方用 appId 绑定。
     */
    private Map<String, String> buildPlatformPackageSqlMapV2(String appId) {
        Map<String, String> map = new LinkedHashMap<>();
        // 应用表按 id 查询，其余按 app_id
        map.put(PlatformTableConstant.APP, String.format("select * from %s where id = ?", PlatformTableConstant.APP));
        map.put(PlatformTableConstant.APP_PAGE, pkgWhereAppId(PlatformTableConstant.APP_PAGE));
        map.put(PlatformTableConstant.TREE_NODE, String.format("select * from %s where tree_id = ?", PlatformTableConstant.TREE_NODE));
        map.put(PlatformTableConstant.DEV_DB_CONNECT, pkgWhereAppId(PlatformTableConstant.DEV_DB_CONNECT));
        map.put(PlatformTableConstant.DEV_DEV_TABLE, pkgWhereAppId(PlatformTableConstant.DEV_DEV_TABLE));
        map.put(PlatformTableConstant.DEV_COLUMN, pkgWhereAppId(PlatformTableConstant.DEV_COLUMN));
        map.put(PlatformTableConstant.DEV_TABLE_FOREIGN, pkgWhereAppId(PlatformTableConstant.DEV_TABLE_FOREIGN));
        map.put(PlatformTableConstant.DEV_VIEW, pkgWhereAppId(PlatformTableConstant.DEV_VIEW));
        map.put(PlatformTableConstant.DICT, pkgWhereAppId(PlatformTableConstant.DICT));
        map.put(PlatformTableConstant.DICT_ITEM, pkgWhereAppId(PlatformTableConstant.DICT_ITEM));
        map.put(PlatformTableConstant.PERMISSION, pkgWhereAppId(PlatformTableConstant.PERMISSION));
        map.put(PlatformTableConstant.ROLE, pkgWhereAppId(PlatformTableConstant.ROLE));
        map.put(PlatformTableConstant.ROLE_R_PERMISSION, pkgWhereAppId(PlatformTableConstant.ROLE_R_PERMISSION));
        map.put(PlatformTableConstant.ROLE_R_TREE_NODE, pkgWhereAppId(PlatformTableConstant.ROLE_R_TREE_NODE));
        map.put(PlatformTableConstant.ROLE_R_APP, pkgWhereAppId(PlatformTableConstant.ROLE_R_APP));
        map.put(PlatformTableConstant.SYS_CONFIG, pkgWhereAppId(PlatformTableConstant.SYS_CONFIG));
        map.put(PlatformTableConstant.EXPORT_TEMPLATE, pkgWhereAppId(PlatformTableConstant.EXPORT_TEMPLATE));
        map.put(PlatformTableConstant.ENCODING, pkgWhereAppId(PlatformTableConstant.ENCODING));
        map.put(PlatformTableConstant.RESOURCES, pkgWhereAppId(PlatformTableConstant.RESOURCES));
        return map;
    }

    private String pkgWhereAppId(String table) {
        return String.format("select * from %s where app_id = ?", table);
    }

    /**
     * 读取指定版本集合的应用包数据（v2），用于合并打包。
     */
    private List<AppPackData> getAppointAppPackageV2(String[] versions) {
        List<AppPackData> appPackageList = new ArrayList<>();
        for (String version : versions) {
            AppVersion appVersion = appVersionService.getAppVersionByVersion(version);
            if (appVersion == null || StringUtils.isEmpty(appVersion.getPackagePath())) {
                continue;
            }
            String data = readPackageDataV2(appVersion);
            appPackageList.add(cn.geelato.pack.utils.PackageUtils.resolveAppPackageData(data));
        }
        return appPackageList;
    }

    /**
     * 读取版本包内容（v2）。优先按文件路径读取，否则按附件ID下载。
     */
    private String readPackageDataV2(AppVersion appVersion) {
        String packagePath = appVersion.getPackagePath();
        try {
            if (packagePath.contains(COMPRESS_PACKAGE_FILE_SUFFIX)) {
                return ZipUtils.readPackageData(packagePath, PACKAGE_FILE_SUFFIX);
            } else {
                File file = fileHandler.toFile(packagePath);
                return ZipUtils.readPackageData(file, PACKAGE_FILE_SUFFIX);
            }
        } catch (IOException ex) {
            throw new PackageException(ex.getMessage());
        }
    }

    private String generateVersionCodeV2(String appCode) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return String.format("%s_version%s", appCode, sdf.format(new Date()));
    }

    /**
     * 写出应用包数据为 .gdp 并压缩为 .zgdp，返回附件ID（v2）。
     */
    private String writePackageDataV2(AppVersion appVersion, AppPackData appPackage) throws PackageException {
        JSON.config(JSONWriter.Feature.LargeObject, true);
        String jsonStr = JSONObject.toJSONString(appPackage);
        String dataFileName = StringUtils.isEmpty(appPackage.getAppCode()) ? DEFAULT_PACKAGE_NAME : appPackage.getAppCode();
        String fileName = dataFileName + PACKAGE_FILE_SUFFIX;
        String tempFolderPath = dataFileName + "/";
        File file = new File(packageConfigurationProperties.getPath() + tempFolderPath + fileName);
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new PackageException("创建打包临时目录失败: " + file.getParentFile().getAbsolutePath());
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(jsonStr);
        } catch (IOException ex) {
            throw new PackageException(ex.getMessage());
        }
        return compressAppPackageV2(packageConfigurationProperties.getPath() + tempFolderPath, appVersion, appPackage);
    }

    private String compressAppPackageV2(String sourcePackageFolder, AppVersion appVersion, AppPackData appPackage) throws PackageException {
        String appPackageName = StringUtils.isEmpty(appPackage.getAppCode()) ? DEFAULT_PACKAGE_NAME : appPackage.getAppCode();
        String appPackageFullName = (Strings.isNotBlank(appVersion.getVersion()) ? appVersion.getVersion() : appPackageName) + COMPRESS_PACKAGE_FILE_SUFFIX;
        String targetZipPath;
        try {
            targetZipPath = UploadService.getRootSavePath(SAVE_TABLE_TYPE, SessionCtx.getCurrentTenantCode(),
                    appPackage.getSourceAppId(), appPackageFullName, true);
        } catch (IOException e) {
            throw new PackageException("获取打包存储路径失败: " + e.getMessage());
        }
        // 注意：ZipUtils.compressDirectory 内部吞掉 IOException（与 v1 行为一致），无需在此捕获；
        // fileHandler.save 声明 throws IOException，需包装为 PackageException。
        ZipUtils.compressDirectory(sourcePackageFolder, targetZipPath);
        File file = new File(targetZipPath);
        FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, "package", appPackage.getSourceAppId(), appVersion.getTenantCode());
        try {
            Attachment attachment = fileHandler.save(file, appPackageFullName, targetZipPath, fileParam);
            return attachment.getId();
        } catch (IOException e) {
            throw new PackageException("保存应用包附件失败: " + e.getMessage());
        }
    }

    // ======================================================================
    // v2 私有助手：部署上下文与删除
    // ======================================================================

    /**
     * v2 部署局部上下文。所有状态均为方法局部，绝不放实例字段，线程安全。
     */
    private static class DeployContext {
        /** 增量表集合（平台增量 ∪ 业务增量）。增量表部署时只插入新行，不清空旧数据。 */
        final Set<String> incrementMetas = new HashSet<>();
        /** 增量表在目标库中已存在的 id 集合，插入时用于跳过。 */
        final Map<String, List<String>> existingIds = new HashMap<>();
    }

    /**
     * 现场重算部署上下文（v2）。
     */
    private DeployContext buildDeployContextV2(String appId) {
        DeployContext ctx = new DeployContext();
        // 平台增量表
        Collections.addAll(ctx.incrementMetas, cn.geelato.pack.utils.AppMetaUtils.getIncrementPlatformMetas());
        // 业务增量表（pack_bus_data=1）
        List<Map<String, Object>> bizTables = dao.getJdbcTemplate().queryForList(
                "select table_name from platform_dev_table where pack_bus_data = 1 and enable_status = 1");
        for (Map<String, Object> row : bizTables) {
            ctx.incrementMetas.add(String.valueOf(row.get("table_name")));
        }
        // 记录增量表已存在 id
        for (String table : ctx.incrementMetas) {
            if (!VALID_TABLE_NAME.matcher(table).matches()) {
                throw new PackageException("非法的增量表名: " + table);
            }
            List<String> ids = dao.getJdbcTemplate().queryForList(
                    String.format("select id from %s where app_id = ?", table), String.class, appId);
            ctx.existingIds.put(table, ids);
        }
        return ctx;
    }

    /**
     * 删除当前版本数据（v2）。增量表跳过删除（保留后增量合并），其余表按 app_id 清空。
     */
    private void deleteCurrentVersionV2(String appId, DeployContext ctx) {
        log.info("----------------------v2 delete version start--------------------");
        Map<String, String> platformRemoveMap = buildPlatformRemoveSqlMapV2(appId);
        Map<String, String> bizRemoveMap = buildBizDataSqlMapV2(appId, "remove");
        Map<String, String> all = new LinkedHashMap<>();
        all.putAll(platformRemoveMap);
        all.putAll(bizRemoveMap);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (ctx.incrementMetas.contains(entry.getKey())) {
                continue; // 增量表不删，插入时跳过已存在 id
            }
            log.info("v2 remove sql：{}", entry.getValue());
            dao.getJdbcTemplate().update(entry.getValue(), appId);
        }
        log.info("----------------------v2 delete version end--------------------");
    }

    /**
     * 构建平台表删除SQL映射（v2）：delete ... where app_id = ?（应用表本身不删）。
     */
    private Map<String, String> buildPlatformRemoveSqlMapV2(String appId) {
        Map<String, String> map = new LinkedHashMap<>();
        // platform_app 本身按 id 删除（部署会重新插入），其余按 app_id
        map.put(PlatformTableConstant.APP, String.format("delete from %s where id = ?", PlatformTableConstant.APP));
        map.put(PlatformTableConstant.APP_PAGE, delWhereAppId(PlatformTableConstant.APP_PAGE));
        map.put(PlatformTableConstant.TREE_NODE, String.format("delete from %s where tree_id = ?", PlatformTableConstant.TREE_NODE));
        map.put(PlatformTableConstant.DEV_DB_CONNECT, delWhereAppId(PlatformTableConstant.DEV_DB_CONNECT));
        map.put(PlatformTableConstant.DEV_DEV_TABLE, delWhereAppId(PlatformTableConstant.DEV_DEV_TABLE));
        map.put(PlatformTableConstant.DEV_COLUMN, delWhereAppId(PlatformTableConstant.DEV_COLUMN));
        map.put(PlatformTableConstant.DEV_TABLE_FOREIGN, delWhereAppId(PlatformTableConstant.DEV_TABLE_FOREIGN));
        map.put(PlatformTableConstant.DEV_VIEW, delWhereAppId(PlatformTableConstant.DEV_VIEW));
        map.put(PlatformTableConstant.DICT, delWhereAppId(PlatformTableConstant.DICT));
        map.put(PlatformTableConstant.DICT_ITEM, delWhereAppId(PlatformTableConstant.DICT_ITEM));
        map.put(PlatformTableConstant.PERMISSION, delWhereAppId(PlatformTableConstant.PERMISSION));
        map.put(PlatformTableConstant.ROLE, delWhereAppId(PlatformTableConstant.ROLE));
        map.put(PlatformTableConstant.ROLE_R_PERMISSION, delWhereAppId(PlatformTableConstant.ROLE_R_PERMISSION));
        map.put(PlatformTableConstant.ROLE_R_TREE_NODE, delWhereAppId(PlatformTableConstant.ROLE_R_TREE_NODE));
        map.put(PlatformTableConstant.ROLE_R_APP, delWhereAppId(PlatformTableConstant.ROLE_R_APP));
        map.put(PlatformTableConstant.SYS_CONFIG, delWhereAppId(PlatformTableConstant.SYS_CONFIG));
        map.put(PlatformTableConstant.EXPORT_TEMPLATE, delWhereAppId(PlatformTableConstant.EXPORT_TEMPLATE));
        map.put(PlatformTableConstant.ENCODING, delWhereAppId(PlatformTableConstant.ENCODING));
        map.put(PlatformTableConstant.RESOURCES, delWhereAppId(PlatformTableConstant.RESOURCES));
        return map;
    }

    private String delWhereAppId(String table) {
        return String.format("delete from %s where app_id = ?", table);
    }

    /**
     * 构建业务数据表SQL映射（v2）。type=package 为查询，remove 为删除。SQL 含 ? 占位。
     */
    private Map<String, String> buildBizDataSqlMapV2(String appId, String type) {
        List<Map<String, Object>> metaData = dao.getJdbcTemplate().queryForList(
                "select table_name from platform_dev_table where pack_bus_data > 0 and enable_status = 1");
        Map<String, String> bizDataSqlMap = new LinkedHashMap<>();
        String preOperateSql;
        switch (type) {
            case "package":
                preOperateSql = "select * from";
                break;
            case "remove":
                preOperateSql = "delete from";
                break;
            default:
                return bizDataSqlMap;
        }
        for (Map<String, Object> row : metaData) {
            String tableName = String.valueOf(row.get("table_name"));
            if (!VALID_TABLE_NAME.matcher(tableName).matches()) {
                throw new PackageException("非法的业务表名: " + tableName);
            }
            bizDataSqlMap.put(tableName, String.format("%s %s where app_id = ?", preOperateSql, tableName));
        }
        return bizDataSqlMap;
    }

    // ======================================================================
    // v2 私有助手：插入
    // ======================================================================

    /**
     * 插入应用包数据（v2）。增量表跳过目标库已存在的 id，其余全量插入。
     */
    @SuppressWarnings("unchecked")
    private void insertPackageDataV2(AppPackData appPackage, DeployContext ctx) {
        for (AppMeta appMeta : appPackage.getAppMetaList()) {
            String appMetaName = appMeta.getMetaName();
            log.info("v2 开始处理元数据：{}", appMetaName);
            Object appMetaData = appMeta.getMetaData();
            EntityMeta entityMeta = metaManager.getByEntityName(appMetaName);
            if (entityMeta == null) {
                log.warn("v2 跳过不存在的实体：{}", appMetaName);
                continue;
            }
            String tableName = entityMeta.getTableName();
            boolean increment = ctx.incrementMetas.contains(tableName);
            List<String> existingIds = increment ? ctx.existingIds.get(tableName) : Collections.emptyList();

            // 直接处理集合，避免无意义的 toJSONString -> parseArray 往返
            List<Map<String, Object>> rows;
            if (appMetaData instanceof List) {
                rows = (List<Map<String, Object>>) appMetaData;
            } else if (appMetaData instanceof Map) {
                rows = Collections.singletonList((Map<String, Object>) appMetaData);
            } else {
                // 兜底：未知类型走 fastjson 解析
                JSONArray jsonArray = JSONArray.parseArray(JSONObject.toJSONString(appMetaData));
                rows = new ArrayList<>();
                for (int i = 0; i < jsonArray.size(); i++) {
                    rows.add(jsonArray.getJSONObject(i));
                }
            }

            List<Map<String, Object>> columnMaps = new ArrayList<>();
            for (Map<String, Object> src : rows) {
                Object idVal = src.get("id");
                if (increment && idVal != null && existingIds != null && existingIds.contains(String.valueOf(idVal))) {
                    continue; // 增量表：目标库已存在该 id，跳过
                }
                Map<String, Object> columnMap = new HashMap<>();
                for (Map.Entry<String, Object> e : src.entrySet()) {
                    String key = e.getKey();
                    if ("id".equals(key)) {
                        columnMap.put("forceId", e.getValue());
                    } else {
                        FieldMeta fieldMeta = entityMeta.getFieldMetaByColumn(key);
                        if (fieldMeta != null) {
                            columnMap.put(fieldMeta.getFieldName(), e.getValue());
                        }
                    }
                }
                columnMaps.add(columnMap);
            }

            Map<String, Object> metaData = new HashMap<>();
            metaData.put(appMetaName, columnMaps);
            List<SaveCommand> saveCommandList = jsonTextSaveParser.parseBatch(JSONObject.toJSONString(metaData), new SessionCtx());
            for (SaveCommand saveCommand : saveCommandList) {
                BoundSql boundSql = sqlManager.generateSaveSql(saveCommand);
                dao.save(boundSql);
            }
            log.info("v2 结束处理元数据：{}", appMetaName);
        }
    }

    // ======================================================================
    // v2 私有助手：备份与刷新
    // ======================================================================

    /**
     * 备份当前版本（v2）。把目标 appId 当前数据打包成一个 backup 版本，独立提交。
     * <p>
     * 注意：此方法不在 deploy 的事务内，部署失败时备份不丢，可用于回滚。
     *
     * @param appId 应用ID
     * @return 备份版本ID
     */
    public String backupCurrentVersionV2(String appId) {
        log.info("----------------------v2 backup version start: {}--------------------", appId);
        AppPackData appPackage = buildAppPackDataV2(appId, null);
        if (StringUtils.isEmpty(appPackage.getAppCode())) {
            log.warn("v2 backup: 应用 {} 无可备份数据，跳过", appId);
            return null;
        }
        AppVersion av = new AppVersion();
        av.setAppId(appId);
        av.setVersion(generateVersionCodeV2(appPackage.getAppCode()) + "_backup");
        av.setDescription("部署前自动备份");
        av.setPackageSource(PackageSourceEnum.PACKET.getValue());
        av.setStatus(PackageStatusEnum.BACKUP.getValue());
        av.setPacketTime(new Date());
        av.setTenantCode(getSessionTenantCode());
        String filePath = writePackageDataV2(av, appPackage);
        av.setPackagePath(filePath);
        AppVersion saved = createModel(av);
        log.info("----------------------v2 backup version end: {}, backupVersionId={} --------------------", appId, saved.getId());
        return saved.getId();
    }

    /**
     * 刷新应用相关的内存元数据缓存（v2）。
     */
    private void refreshAppV2(String appId) {
        Collection<EntityMeta> allEntityMeta = metaManager.getAll();
        for (EntityMeta entityMeta : allEntityMeta) {
            String metaAppId = entityMeta.getTableMeta().getAppId();
            if (metaAppId != null && metaAppId.equals(appId)) {
                metaManager.refreshDBMeta(entityMeta.getEntityName());
            }
        }
    }
}
