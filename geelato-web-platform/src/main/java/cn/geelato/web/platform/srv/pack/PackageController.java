package cn.geelato.web.platform.srv.pack;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.orm.Dao;
import cn.geelato.pack.entity.AppMeta;
import cn.geelato.pack.entity.AppPackData;
import cn.geelato.pack.enums.PackageSourceEnum;
import cn.geelato.pack.enums.PackageStatusEnum;
import cn.geelato.pack.utils.AppMetaUtils;
import cn.geelato.pack.utils.PackageUtils;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.parser.JsonTextSaveParser;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.base.service.UploadService;
import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.srv.file.param.FileParam;
import cn.geelato.web.platform.utils.FileParamUtils;
import cn.geelato.pack.PackageConfigurationProperties;
import cn.geelato.web.platform.srv.pack.exception.PackException;
import cn.geelato.meta.AppVersion;
import cn.geelato.web.platform.srv.pack.service.AppVersionService;
import cn.geelato.web.platform.srv.pack.service.PackageService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping(value = "/package")
@Slf4j
public class PackageController {

    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;
    private final String defaultPackageName = "geelatoApp";
    private static final String SAVE_TABLE_TYPE = AttachmentSourceEnum.ATTACH.getValue();

    private final ArrayList<String> incrementMetas = new ArrayList<>();

    private final ArrayList<String> incrementBizMetas = new ArrayList<>();

    private final Map<String, List<String>> incrementMetaIds = new HashMap<>();
    @Resource
    private PackageConfigurationProperties packageConfigurationProperties;
    @Resource
    private FileHandler fileHandler;
    @Resource
    AppVersionService appVersionService;
    @Resource
    private PackageService packageService;

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final JsonTextSaveParser jsonTextSaveParser = new JsonTextSaveParser();

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    @ModelAttribute
    public void setReqAndRes(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    /*
    打包应用
     */
    @RequestMapping(value = {"/packet/{appId}"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<AppVersion> packetApp(@NotNull @PathVariable("appId") String appId, String version, String description,
                                           @RequestBody(required = false) Map<String, String> appointMetas) throws IOException {
        if ("v2".equals(packageConfigurationProperties.getEngine())) {
            return ApiResult.success(packageService.packetV2(appId, version, description, appointMetas));
        }
        long startTime = System.currentTimeMillis();
        log.info("====================== v1 pack start ======================");
        log.info("打包应用：appId={}, 指定元数据={}", appId, appointMetas == null ? "全部" : appointMetas.keySet());
        Map<String, String> appDataMap = new HashMap<>();
        Map<String, String> appMetaDataMap = AppMetaUtils.buildPackageAppMetaMap(appId);
        Map<String, String> appBizDataMap = appBizDataMap(appId, "package");
        appDataMap.putAll(appMetaDataMap);
        appDataMap.putAll(appBizDataMap);
        AppPackData appPackage = new AppPackData();
        List<AppMeta> appMetaList = new ArrayList<>();
        Map<String, List<String>> inconsistentTables = new LinkedHashMap<>();
        String basePlatformVersion = dao.getJdbcTemplate().queryForMap("select version_info from platform_app where code='geelato_admin'").
                get("version_info").toString();
        appPackage.setBasePlatformVersion(basePlatformVersion);
        for (String key : appDataMap.keySet()) {
            String value = appDataMap.get(key);
            List<Map<String, Object>> metaData = dao.getJdbcTemplate().queryForList(value);
            List<String> unknownColumns = packageService.findUnknownColumns(key, metaData);
            if (!unknownColumns.isEmpty()) {
                inconsistentTables.put(key, unknownColumns);
            }
            log.info("打包表 [{}]：{} 行", key, metaData.size());
            if ("platform_app".equals(key) && !metaData.isEmpty()) {
                appPackage.setAppCode(metaData.get(0).get("code").toString());
                appPackage.setAppName(metaData.get(0).get("name").toString());
                appPackage.setSourceAppId(appId);
            } else {
                if (appointMetas != null) {
                    if (appointMetas.containsKey(key)) {
                        List<Map<String, Object>> appointMetaData = PackageUtils.pickMetaData(metaData, appointMetas.get(key));
                        AppMeta appMeta = new AppMeta(key, appointMetaData);
                        appMetaList.add(appMeta);
                    }
                } else {
                    AppMeta appMeta = new AppMeta(key, metaData);
                    appMetaList.add(appMeta);
                }

            }
        }
        packageService.assertColumnsConsistent(inconsistentTables);
        if (StringUtils.isEmpty(appPackage.getAppCode())) {
            log.warn("打包失败：找不到可打包的应用，appId={}", appId);
            throw new PackException(PackException.ERROR_CODE_APP_NOT_FOUND, "找不到可打包的应用");
        }
        appPackage.setAppMetaList(appMetaList);
        AppVersion av = new AppVersion();
        av.setAppId(appId);
        String packageVersion;
        if (StringUtils.isEmpty(version)) {
            packageVersion = generateVersionCode(appPackage.getAppCode());
        } else {
            packageVersion = version;
        }
        av.setVersion(packageVersion);
        appPackage.setVersion(packageVersion);
        if (StringUtils.isEmpty(description)) {
            av.setDescription("当前环境打包形成的应用包");
        } else {
            av.setDescription(description);
        }
        av.setPackageSource(PackageSourceEnum.PACKET.getValue());
        av.setStatus(PackageStatusEnum.DRAFT.getValue());
        av.setPacketTime(new Date());
        String filePath = writePackageData(av, appPackage);
        av.setPackagePath(filePath);
        log.info("打包完成：appCode={}, 版本={}, 基准平台版本={}, 元数据 {} 张, 包文件={}, 耗时 {} ms",
                appPackage.getAppCode(), packageVersion, appPackage.getBasePlatformVersion(),
                appMetaList.size(), filePath, System.currentTimeMillis() - startTime);

        return ApiResult.success(appVersionService.createModel(av));
    }

    @RequestMapping(value = {"/packet/merge"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<AppVersion> packetMergeApp(String appId, String version, String description,
                                                @RequestBody(required = false) Map<String, Map<String, String>> appointMetas) throws IOException {
        if ("v2".equals(packageConfigurationProperties.getEngine())) {
            return ApiResult.success(packageService.packetMergeV2(appId, version, description, appointMetas));
        }
        long startTime = System.currentTimeMillis();
        String[] versionIds = appointMetas.keySet().toArray(new String[0]);
        log.info("====================== v1 pack merge start ======================");
        log.info("合并打包：appId={}, 版本ids={}", appId, Arrays.toString(versionIds));
        List<AppPackData> appPackages = getAppointAppPackage(versionIds);
        AppPackData appPackage = PackageUtils.mergePackage(appPackages, appointMetas);
        AppVersion av = new AppVersion();
        av.setAppId(appId);
        if (StringUtils.isEmpty(version)) {
            av.setVersion(generateVersionCode(appPackage.getAppCode()));
        } else {
            av.setVersion(version);
        }
        if (StringUtils.isEmpty(description)) {
            av.setDescription("对比合并产生的包");
        } else {
            av.setDescription(description);
        }

        av.setPackageSource(PackageSourceEnum.PACKET.getValue());
        av.setStatus(PackageStatusEnum.DRAFT.getValue());
        av.setPacketTime(new Date());
        String filePath = writePackageData(av, appPackage);
        av.setPackagePath(filePath);
        log.info("合并打包完成：appCode={}, 元数据 {} 张, 包文件={}, 耗时 {} ms",
                appPackage.getAppCode(),
                appPackage.getAppMetaList() == null ? 0 : appPackage.getAppMetaList().size(),
                filePath, System.currentTimeMillis() - startTime);
        return ApiResult.success(appVersionService.createModel(av));
    }





    private List<AppPackData> getAppointAppPackage(String[] versions) {
        List<AppPackData> appPackageList = new ArrayList<>();
        for (String version : versions) {
            AppVersion appVersion = appVersionService.getAppVersionByVersion(version);
            String appPackageData;
            if (appVersion != null && !StringUtils.isEmpty(appVersion.getPackagePath())) {
                try {
                    if (appVersion.getPackagePath().contains(".zgdp")) {
                        appPackageData = ZipUtils.readPackageData(appVersion.getPackagePath(), ".gdp");
                    } else {
                        File file = fileHandler.toFile(appVersion.getPackagePath());
                        appPackageData = ZipUtils.readPackageData(file, ".gdp");
                    }
                } catch (IOException ex) {
                    throw new PackException(PackException.ERROR_CODE_PACKAGE_IO, ex.getMessage());
                }
                AppPackData appPackage =PackageUtils.resolveAppPackageData(appPackageData);
                appPackageList.add(appPackage);
            }
        }
        return appPackageList;
    }



    private String generateVersionCode(String appCode) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String dateStr = sdf.format(new Date());
        return String.format("%s_version%s", appCode, dateStr);
    }

    /*
    下载版本包
     */
    @RequestMapping(value = {"/downloadPackage/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public void downloadPackage(@PathVariable("versionId") String versionId) throws IOException {
        AppVersion appVersion = appVersionService.getModel(AppVersion.class, versionId);
        String filePath = appVersion.getPackagePath();
        File file = new File(filePath);
        FileInputStream fileInputStream = new FileInputStream(file);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        response.setContentType(MediaTypes.APPLICATION_OCTET_STREAM);
        OutputStream outputStream = response.getOutputStream();
        int bytesRead;
        byte[] buffer = new byte[4096];
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        fileInputStream.close();
        outputStream.close();
    }

    /*
    上传版本包
     */
    @RequestMapping(value = {"/uploadPackage/{appId}"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<AppVersion> uploadPackage(@RequestParam("file") MultipartFile file, @PathVariable("appId") String appId) throws IOException {
        byte[] bytes = file.getBytes();
        String targetPath = packageConfigurationProperties.getUploadPath() + file.getOriginalFilename();
        Files.write(Path.of(targetPath), bytes);
        AppVersion av = new AppVersion();
        av.setAppId(appId);
        av.setPacketTime(new Date());
        av.setPackagePath(targetPath);
        return ApiResult.success(appVersionService.createModel(av));
    }

    /*
    部署版本包
     */
    @RequestMapping(value = {"/deploy/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<?> deployPackage(@PathVariable("versionId") String versionId) {
        if ("v2".equals(packageConfigurationProperties.getEngine())) {
            return packageService.deployV2(versionId);
        }
        if ("init_source".equals(packageConfigurationProperties.getEnv())) {
            throw new PackException(PackException.ERROR_CODE_NOT_ALLOWED, "本环境无法部署任何应用，请联系管理员！");
        }
        long startTime = System.currentTimeMillis();
        AppVersion appVersion = appVersionService.getModel(AppVersion.class, versionId);
        String appPackageData;
        if (appVersion != null && !StringUtils.isEmpty(appVersion.getPackagePath())) {
            log.info("应用部署开始：versionId={}, appId={}, 包文件={}", versionId, appVersion.getAppId(), appVersion.getPackagePath());
            try {
                if (appVersion.getPackagePath().contains(".zgdp")) {
                    appPackageData = ZipUtils.readPackageData(appVersion.getPackagePath(), ".gdp");
                    // 测试用
//                    appPackageData = ZipUtils.readPackageData("D:\\ob_v1.1.1.20250218201957.zgdp", ".gdp");
                } else {
//                    appPackageData = ZipUtils.readPackageData("D:\\ob_v1.1.1.20250218201957.zgdp", ".gdp");
                    File file = fileHandler.toFile(appVersion.getPackagePath());
                    appPackageData = ZipUtils.readPackageData(file, ".gdp");
                }
            } catch (IOException ex) {
                log.error("读取应用包文件失败，versionId: {}, packagePath: {}", versionId, appVersion.getPackagePath(), ex);
                throw new PackException(PackException.ERROR_CODE_PACKAGE_IO, "读取应用包文件失败（" + appVersion.getPackagePath() + "）：" + rootMsg(ex));
            }

            AppPackData appPackage = PackageUtils.resolveAppPackageData(appPackageData);
            if (appPackage != null && !appPackage.getAppMetaList().isEmpty()) {
                log.info("应用包解析完成：appCode={}, 基准平台版本={}, 元数据 {} 张",
                        appPackage.getAppCode(), appPackage.getBasePlatformVersion(), appPackage.getAppMetaList().size());
                try {
                    if(PackageUtils.validatePackageData(appPackage,metaManager.getAll())){
                        backupCurrentVersion(appVersion.getAppId());
                        deployAppPackageData(appPackage);
                    }else {
                        throw new PackException(PackException.ERROR_CODE_PLATFORM_MISMATCH,
                                "应用包校验不通过,请先更新平台应用geelato_admin至版本" + appPackage.getBasePlatformVersion());
                    }
                } catch (PackException pe) {
                    throw pe;
                } catch (Exception ex) {
                    log.error("应用部署失败，versionId: {}, appId: {}", versionId, appVersion.getAppId(), ex);
                    throw new PackException(PackException.ERROR_CODE_DEPLOY_DATA_FAILED,
                            "应用部署失败：" + PackageService.withFieldMetaHint(rootMsg(ex)));
                }
                try {
                    refreshApp(appVersion.getAppId());
                } catch (Exception ex) {
                    log.error("应用数据已部署成功，但刷新应用元数据缓存失败，appId: {}", appVersion.getAppId(), ex);
                    throw new PackException(PackException.ERROR_CODE_REFRESH_CACHE_FAILED,
                            "应用数据已部署成功，但刷新应用元数据缓存失败：" + rootMsg(ex));
                }
                log.info("应用部署成功：versionId={}, appId={}, 耗时 {} ms", versionId, appVersion.getAppId(), System.currentTimeMillis() - startTime);
            } else {
                throw new PackException(PackException.ERROR_CODE_PACKAGE_INVALID, "无法读取到应用包数据，请检查应用包");
            }
        }
        return ApiResult.success(null, "应用部署成功！");
    }

    /*
    回滚到最近一次备份版本（仅 v2）
     */
    @RequestMapping(value = {"/rollback/{appId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<?> rollback(@PathVariable("appId") String appId) {
        return packageService.rollbackV2(appId);
    }

    private void refreshApp(String appId) {
        int refreshed = 0;
        int skippedPlatform = 0;
        List<EntityMeta> allEntityMeta = MetaManager.singleInstance().getAll().stream().toList();
        for (EntityMeta entityMeta : allEntityMeta) {
            if (entityMeta.getTableMeta().getAppId() != null && entityMeta.getTableMeta().getAppId().equals(appId)) {
                if ("platform".equalsIgnoreCase(entityMeta.getCatalog())) {
                    skippedPlatform++;
                    continue;
                }
                MetaManager.singleInstance().refreshDBMeta(entityMeta.getEntityName());
                refreshed++;
            }
        }
        log.info("刷新应用元数据缓存完成：appId={}, 刷新实体 {} 个, 跳过平台实体 {} 个", appId, refreshed, skippedPlatform);
    }

    // todo
    private void backupCurrentVersion(String appId) {
        log.info("----------------------backup version start--------------------");
        Map<String, String> appMetaMap = AppMetaUtils.buildRemoveAppMetaMap(appId);
        for (String key : appMetaMap.keySet()) {
            String value = appMetaMap.get(key);
        }
        log.info("----------------------backup version end--------------------");
    }

    private void deleteCurrentVersion(String appId) {
        log.info("----------------------delete version start--------------------");
        Map<String, String> appDataMap = new HashMap<>();
        Map<String, String> appMetaDataMap = AppMetaUtils.buildRemoveAppMetaMap(appId);
        Map<String, String> appBizDataMap = appBizDataMap(appId, "remove");
        appDataMap.putAll(appMetaDataMap);
        appDataMap.putAll(appBizDataMap);
        for (String key : appDataMap.keySet()) {
            String value = appDataMap.get(key);
            if (incrementMetas.contains(key)) {
                // 如果增量更新，不执行清空数据操作
                String sql = String.format("select id from " + key + " where app_id='%s'", appId);
                List<String> ids = dao.getJdbcTemplate().queryForList(sql, String.class);
                incrementMetaIds.put(key, ids);
                continue;
            }

            log.info("remove sql：{} ", value);
            dao.getJdbcTemplate().execute(value);
        }
        log.info("----------------------delete version end--------------------");
    }

    private Map<String, String> appBizDataMap(String appId, String type) {
        String sql = "select table_name,pack_bus_data from platform_dev_table where pack_bus_data > 0  and enable_status =1";
        List<Map<String, Object>> metaData = dao.getJdbcTemplate().queryForList(sql);
        Map<String, String> bizDataSqlMap = new HashMap<>();
        for (Map map : metaData) {
            String preOperateSql = "";
            switch (type) {
                case "package":
                    preOperateSql = "select * from ";
                    break;
                case "remove":
                    preOperateSql = "delete from  ";
                    break;
                default:
                    break;
            }
            String tableName = map.get("table_name").toString();
            String packBusData = map.get("pack_bus_data").toString();
            String bizSql = String.format("%s %s where app_id ='%s'", preOperateSql, tableName, appId);
            bizDataSqlMap.put(tableName, bizSql);
            if ("1".equals(packBusData)) {
                incrementBizMetas.add(tableName);
            }
        }
        incrementMetas.addAll(incrementBizMetas);
        return bizDataSqlMap;
    }

    private String writePackageData(AppVersion appVersion, AppPackData appPackage) throws IOException {
        JSON.config(JSONWriter.Feature.LargeObject,true);
        String jsonStr = JSONObject.toJSONString(appPackage);
        String packageSuffix = ".gdp";
        String dataFileName = StringUtils.isEmpty(appPackage.getAppCode()) ? defaultPackageName : appPackage.getAppCode();
        String fileName = dataFileName + packageSuffix;
        String tempFolderPath = dataFileName + "/";
        File file = new File(packageConfigurationProperties.getPath() + tempFolderPath + fileName);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdir();
        }
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(jsonStr);
            writer.close();
        } catch (IOException ex) {
            throw new PackException(PackException.ERROR_CODE_PACKAGE_IO, ex.getMessage());
        }
        return compressAppPackage(packageConfigurationProperties.getPath() + tempFolderPath, appVersion, appPackage);
    }

    private String compressAppPackage(String sourcePackageFolder, AppVersion appVersion, AppPackData appPackage) throws IOException {
        String packageSuffix = ".zgdp";
        String appPackageName = StringUtils.isEmpty(appPackage.getAppCode()) ? defaultPackageName : appPackage.getAppCode();
        String appPackageFullName = (Strings.isNotBlank(appVersion.getVersion()) ? appVersion.getVersion() : appPackageName) + packageSuffix;
        String targetZipPath;
        targetZipPath = UploadService.getRootSavePath(SAVE_TABLE_TYPE, SessionCtx.getCurrentTenantCode(),
                appPackage.getSourceAppId(), appPackageFullName, true);
        ZipUtils.compressDirectory(sourcePackageFolder, targetZipPath);
        File file = new File(targetZipPath);
        FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, "package", appPackage.getSourceAppId(), appVersion.getTenantCode());
        Attachment attachment = fileHandler.save(file, appPackageFullName, targetZipPath, fileParam);
        return attachment.getId();
    }


    private void deployAppPackageData(AppPackData appPackage) {
        log.info("----------------------deploy start--------------------");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dao.getJdbcTemplate().getDataSource());
        TransactionStatus status = TransactionHelper.beginTransaction(transactionManager);
        try {
            deleteCurrentVersion(appPackage.getSourceAppId());
            List<AppMeta> appMetaList = appPackage.getAppMetaList();
            for (int idx = 0; idx < appMetaList.size(); idx++) {
                AppMeta appMeta = appMetaList.get(idx);
                log.info("开始处理元数据：{}（第 {}/{} 个）", appMeta.getMetaName(), idx + 1, appMetaList.size());
                try {
                    deployAppMetaData(appMeta);
                } catch (Exception ex) {
                    log.error("处理元数据 [{}] 失败（第 {}/{} 个）", appMeta.getMetaName(), idx + 1, appMetaList.size(), ex);
                    throw new PackException(PackException.ERROR_CODE_DEPLOY_DATA_FAILED, "处理元数据 [" + appMeta.getMetaName() + "] 失败：" + rootMsg(ex));
                }
                log.info("结束处理元数据：{}", appMeta.getMetaName());
            }
            TransactionHelper.commitTransaction(transactionManager, status);
        } catch (Exception ex) {
            if (!status.isCompleted()) {
                TransactionHelper.rollbackTransaction(transactionManager, status);
                log.info("部署事务已回滚，appId: {}", appPackage.getSourceAppId());
            }
            throw ex;
        }
        log.info("----------------------deploy end--------------------");
    }

    private void deployAppMetaData(AppMeta appMeta) {
        Map<String, Object> metaData = new HashMap<>();
        ArrayList<Map<String, Object>> metaDataArray = new ArrayList<>();
        String appMetaName = appMeta.getMetaName();
        Object appMetaData = appMeta.getMetaData();
        EntityMeta entityMeta = metaManager.getByEntityName(appMetaName);
        if (entityMeta == null) {
            throw new PackException(PackException.ERROR_CODE_META_NOT_FOUND, "元数据 [" + appMetaName + "] 在当前平台不存在（平台版本过低），无法部署");
        }
        String tableName = entityMeta.getTableName();
        boolean increment = incrementMetas.contains(tableName);
        List<String> ids = null;
        if (increment) {
            ids = incrementMetaIds.get(tableName);
        }
        JSONArray jsonArray = JSONArray.parseArray(JSONObject.toJSONString(appMetaData));
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jo = jsonArray.getJSONObject(i);
            Map<String, Object> columnMap = new HashMap<>();
            boolean upgradeToTarget = true;
            for (String key : jo.keySet()) {
                FieldMeta fieldMeta = entityMeta.getFieldMetaByColumn(key);
                if ("id".equals(key)) {
                    if (increment) {
                        if (ids.contains(jo.get(key).toString())) {
                            upgradeToTarget = false;
                        }
                    }
                    columnMap.put("forceId", jo.get(key));
                } else {
                    if (fieldMeta == null) {
                        throw new PackException(PackException.ERROR_CODE_META_NOT_FOUND,
                                "应用包中的字段 [" + key + "] 在元数据 [" + appMetaName + "] 中不存在（平台版本不匹配），无法部署");
                    }
                    columnMap.put(fieldMeta.getFieldName(), jo.get(key));
                }
            }
            if (upgradeToTarget) {
                metaDataArray.add(columnMap);
            }
        }
        metaData.put(appMeta.getMetaName(), metaDataArray);
        List<SaveCommand> saveCommandList = jsonTextSaveParser.parseBatch(JSONObject.toJSONString(metaData), new SessionCtx());
        for (SaveCommand saveCommand : saveCommandList) {
            BoundSql boundSql = sqlManager.generateSaveSql(saveCommand);
            dao.save(boundSql);
        }
    }

    private String rootMsg(Throwable ex) {
        return StringUtils.isEmpty(ex.getMessage()) ? ex.toString() : ex.getMessage();
    }
}
