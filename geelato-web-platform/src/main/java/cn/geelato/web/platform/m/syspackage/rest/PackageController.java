package cn.geelato.web.platform.m.syspackage.rest;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.orm.Dao;
import cn.geelato.syspackage.core.PackageService;
import cn.geelato.syspackage.entity.AppMeta;
import cn.geelato.syspackage.entity.AppPackage;
import cn.geelato.syspackage.enums.PackageSourceEnum;
import cn.geelato.syspackage.enums.PackageStatusEnum;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.parser.JsonTextSaveParser;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.DaoException;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.ZipUtils;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import cn.geelato.syspackage.PackageConfigurationProperties;
import cn.geelato.syspackage.PackageException;
import cn.geelato.web.platform.m.syspackage.entity.AppVersion;
import cn.geelato.web.platform.m.syspackage.service.AppVersionService;
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
    private DataSourceTransactionManager dataSourceTransactionManager;
    private TransactionStatus transactionStatus;
    private final String defaultPackageName = "geelatoApp";
    private static final String SAVE_TABLE_TYPE = AttachmentSourceEnum.ATTACH.getValue();

    private final ArrayList<String> incrementMetas = new ArrayList<>();

    private final String[] incrementPlatformMetas = {"platform_dict", "platform_dict_item", "platform_sys_config", "platform_encoding", "platform_resources"};

    private final ArrayList<String> incrementBizMetas = new ArrayList<>();

    private final Map<String, List<String>> incrementMetaIds = new HashMap<>();
    @Resource
    private PackageConfigurationProperties packageConfigurationProperties;
    @Resource
    private FileHandler fileHandler;
    @Resource
    AppVersionService appVersionService;

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
        Map<String, String> appDataMap = new HashMap<>();
        Map<String, String> appMetaDataMap = appMetaMap(appId, "package");
        Map<String, String> appBizDataMap = appBizDataMap(appId, "package");
        appDataMap.putAll(appMetaDataMap);
        appDataMap.putAll(appBizDataMap);
        AppPackage appPackage = new AppPackage();
        List<AppMeta> appMetaList = new ArrayList<>();
        String basePlatformVersion = dao.getJdbcTemplate().queryForMap("select version_info from platform_app where code='geelato_admin'").
                get("version_info").toString();
        appPackage.setBasePlatformVersion(basePlatformVersion);
        for (String key : appDataMap.keySet()) {
            String value = appDataMap.get(key);
            List<Map<String, Object>> metaData = dao.getJdbcTemplate().queryForList(value);
            if ("platform_app".equals(key) && !metaData.isEmpty()) {
                appPackage.setAppCode(metaData.get(0).get("code").toString());
                appPackage.setAppName(metaData.get(0).get("name").toString());
                appPackage.setSourceAppId(appId);
            } else {
                if (appointMetas != null) {
                    if (appointMetas.containsKey(key)) {
                        List<Map<String, Object>> appointMetaData = PackageService.pickMetaData(metaData, appointMetas.get(key));
                        AppMeta appMeta = new AppMeta(key, appointMetaData);
                        appMetaList.add(appMeta);
                    }
                } else {
                    AppMeta appMeta = new AppMeta(key, metaData);
                    appMetaList.add(appMeta);
                }

            }
        }
        if (StringUtils.isEmpty(appPackage.getAppCode())) {
            return ApiResult.fail("找不到可打包的应用");
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

        return ApiResult.success(appVersionService.createModel(av));
    }

    @RequestMapping(value = {"/packet/merge"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<AppVersion> packetMergeApp(String appId, String version, String description,
                                                @RequestBody(required = false) Map<String, Map<String, String>> appointMetas) throws IOException {
        String[] versionIds = appointMetas.keySet().toArray(new String[0]);
        List<AppPackage> appPackages = getAppointAppPackage(versionIds);
        AppPackage appPackage = PackageService.mergePackage(appPackages, appointMetas);
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
        return ApiResult.success(appVersionService.createModel(av));
    }





    private List<AppPackage> getAppointAppPackage(String[] versions) {
        List<AppPackage> appPackageList = new ArrayList<>();
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
                    throw new PackageException(ex.getMessage());
                }
                AppPackage appPackage =PackageService.resolveAppPackageData(appPackageData);
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
    public ApiResult<?> deployPackage(@PathVariable("versionId") String versionId) throws DaoException {
        if ("init_source".equals(packageConfigurationProperties.getEnv())) {
            return ApiResult.fail("本环境无法部署任何应用，请联系管理员！");
        }
        AppVersion appVersion = appVersionService.getModel(AppVersion.class, versionId);
        String appPackageData;
        if (appVersion != null && !StringUtils.isEmpty(appVersion.getPackagePath())) {
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
                throw new PackageException(ex.getMessage());
            }

            AppPackage appPackage = PackageService.resolveAppPackageData(appPackageData);
            if (appPackage != null && !appPackage.getAppMetaList().isEmpty()) {
                try {
                    if(PackageService.validatePackageData(appPackage,metaManager.getAll())){
                        backupCurrentVersion(appVersion.getAppId());
                        deployAppPackageData(appPackage);
                        refreshApp(appVersion.getAppId());
                    }else {
                        throw new PackageException("应用包校验不通过,请先更新平台应用geelato_admin至版本" + appPackage.getBasePlatformVersion());
                    }
                } catch (Exception ex) {
                    if (transactionStatus != null) {
                        dataSourceTransactionManager.rollback(transactionStatus);
                    }
                    return ApiResult.fail(ex.getMessage());
                }
            } else {
                throw new PackageException("无法读取到应用包数据，请检查应用包");
            }
        }
        return ApiResult.success(null, "应用部署成功！");
    }

    private void refreshApp(String appId) {
        List<EntityMeta> allEntityMeta = MetaManager.singleInstance().getAll().stream().toList();
        for (EntityMeta entityMeta : allEntityMeta) {
            if (entityMeta.getTableMeta().getAppId() != null && entityMeta.getTableMeta().getAppId().equals(appId)) {
                MetaManager.singleInstance().refreshDBMeta(entityMeta.getEntityName());
            }
        }

    }

    // todo
    private void backupCurrentVersion(String appId) {
        log.info("----------------------backup version start--------------------");
        Map<String, String> appMetaMap = appMetaMap(appId, "remove");
        for (String key : appMetaMap.keySet()) {
            String value = appMetaMap.get(key);
        }
        log.info("----------------------backup version end--------------------");
    }

    private void deleteCurrentVersion(String appId) {
        log.info("----------------------delete version start--------------------");
        Map<String, String> appDataMap = new HashMap<>();
        Map<String, String> appMetaDataMap = appMetaMap(appId, "remove");
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



    private Map<String, String> appMetaMap(String appId, String type) {
        Map<String, String> map = new HashMap<>();
        String preOperateSql = "";
        switch (type) {
            case "package":
                preOperateSql = "select * from ";
                map.put("platform_app", String.format("%s platform_app where id='%s'", preOperateSql, appId));
                break;
            case "remove":
                preOperateSql = "delete from  ";
                break;
            default:
                break;
        }
        map.put("platform_app_page", String.format("%s  platform_app_page where app_id='%s' ", preOperateSql, appId));
        map.put("platform_tree_node", String.format("%s  platform_tree_node where tree_id='%s' ", preOperateSql, appId));
        map.put("platform_dev_db_connect", String.format("%s platform_dev_db_connect where app_id='%s' ", preOperateSql, appId));
        map.put("platform_dev_table", String.format("%s  platform_dev_table where app_id='%s' ", preOperateSql, appId));
        map.put("platform_dev_column", String.format("%s  platform_dev_column where app_id='%s' ", preOperateSql, appId));
        map.put("platform_dev_table_foreign", String.format("%s  platform_dev_table_foreign where app_id='%s' ", preOperateSql, appId));
        map.put("platform_dev_view", String.format("%s  platform_dev_view where app_id='%s' ", preOperateSql, appId));
        map.put("platform_dict", String.format("%s  platform_dict where app_id='%s' ", preOperateSql, appId));
        map.put("platform_dict_item", String.format("%s  platform_dict_item where app_id='%s' ", preOperateSql, appId));
        map.put("platform_permission", String.format("%s platform_permission where app_id='%s' ", preOperateSql, appId));
        map.put("platform_role", String.format("%s  platform_role where app_id='%s' ", preOperateSql, appId));
        map.put("platform_role_r_permission", String.format("%s  platform_role_r_permission where app_id='%s'", preOperateSql, appId));
        map.put("platform_role_r_tree_node", String.format("%s  platform_role_r_tree_node where app_id='%s' ", preOperateSql, appId));
        map.put("platform_role_r_app", String.format("%s  platform_role_r_app where app_id='%s' ", preOperateSql, appId));
        map.put("platform_sys_config", String.format("%s  platform_sys_config where app_id='%s' ", preOperateSql, appId));
        map.put("platform_export_template", String.format("%s  platform_export_template where app_id='%s' ", preOperateSql, appId));
        map.put("platform_encoding", String.format("%s  platform_encoding where app_id='%s' ", preOperateSql, appId));
        map.put("platform_resources", String.format("%s  platform_resources where app_id='%s' ", preOperateSql, appId));
        incrementMetas.addAll(Arrays.asList(incrementPlatformMetas));
        return map;
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

    private String writePackageData(AppVersion appVersion, AppPackage appPackage) throws IOException {
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
            throw new PackageException(ex.getMessage());
        }
        return compressAppPackage(packageConfigurationProperties.getPath() + tempFolderPath, appVersion, appPackage);
    }

    private String compressAppPackage(String sourcePackageFolder, AppVersion appVersion, AppPackage appPackage) throws IOException {
        String packageSuffix = ".zgdp";
        String appPackageName = StringUtils.isEmpty(appPackage.getAppCode()) ? defaultPackageName : appPackage.getAppCode();
        String appPackageFullName = (Strings.isNotBlank(appVersion.getVersion()) ? appVersion.getVersion() : appPackageName) + packageSuffix;
        String targetZipPath;
        targetZipPath = UploadService.getRootSavePath(SAVE_TABLE_TYPE, SessionCtx.getCurrentTenantCode(), appPackage.getSourceAppId(), appPackageFullName, true);
        ZipUtils.compressDirectory(sourcePackageFolder, targetZipPath);
        File file = new File(targetZipPath);
        FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, "package", appPackage.getSourceAppId(), appVersion.getTenantCode());
        Attachment attachment = fileHandler.save(file, appPackageFullName, targetZipPath, fileParam);
        return attachment.getId();
    }


    private void deployAppPackageData(AppPackage appPackage) throws DaoException {
        log.info("----------------------deploy start--------------------");
        dataSourceTransactionManager = new DataSourceTransactionManager(dao.getJdbcTemplate().getDataSource());
        transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
        deleteCurrentVersion(appPackage.getSourceAppId());
        for (AppMeta appMeta : appPackage.getAppMetaList()) {
            log.info("开始处理元数据：{}", appMeta.getMetaName());
            Map<String, Object> metaData = new HashMap<>();
            ArrayList<Map<String, Object>> metaDataArray = new ArrayList<>();
            String appMetaName = appMeta.getMetaName();
            Object appMetaData = appMeta.getMetaData();
            EntityMeta entityMeta = metaManager.getByEntityName(appMetaName);
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
            log.info("结束处理元数据：{}", appMeta.getMetaName());
        }
        TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
        log.info("----------------------deploy end--------------------");
    }
}
