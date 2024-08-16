package cn.geelato.web.platform.m.syspackage.rest;

import cn.geelato.utils.StringUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.Ctx;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiResultCode;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.gql.execute.BoundSql;
import cn.geelato.core.gql.parser.JsonTextSaveParser;
import cn.geelato.core.gql.parser.SaveCommand;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.DaoException;
import cn.geelato.core.orm.TransactionHelper;
import cn.geelato.core.sql.SqlManager;
import cn.geelato.utils.ZipUtils;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.enums.PackageSourceEnum;
import cn.geelato.web.platform.enums.PackageStatusEnum;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.DownloadService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.syspackage.PackageConfigurationProperties;
import cn.geelato.web.platform.m.syspackage.entity.AppMeta;
import cn.geelato.web.platform.m.syspackage.entity.AppPackage;
import cn.geelato.web.platform.m.syspackage.entity.AppVersion;
import cn.geelato.web.platform.m.syspackage.service.AppVersionService;
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
public class PackageController extends BaseController {
    private DataSourceTransactionManager dataSourceTransactionManager;
    private TransactionStatus transactionStatus;
    private final String defaultPackageName = "geelatoApp";

    private final ArrayList<String> incrementMetas = new ArrayList<>();

    private final String[] incrementPlatformMetas = {"platform_dict", "platform_dict_item", "platform_sys_config",
            "platform_export_template", "platform_encoding", "platform_resources"};

    private final ArrayList<String> incrementBizMetas = new ArrayList<>();


    private final Map<String, List<String>> incrementMetaIds = new HashMap<>();
    @Resource
    private PackageConfigurationProperties packageConfigurationProperties;
    @Resource
    private AttachService attachService;
    @Resource
    private DownloadService downloadService;
    @Resource
    AppVersionService appVersionService;

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final JsonTextSaveParser jsonTextSaveParser = new JsonTextSaveParser();


    /*
    打包应用
     */
    @RequestMapping(value = {"/packet/{appId}"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult packetApp(@NotNull @PathVariable("appId") String appId, String version, String description,
                               @RequestBody(required = false) Map<String, String> appointMetas) throws IOException {
        ApiResult apiResult = new ApiResult();
        Map<String, String> appDataMap = new HashMap<>();
        Map<String, String> appMetaDataMap = appMetaMap(appId, "package");
        Map<String, String> appBizDataMap = appBizDataMap(appId, "package");
        appDataMap.putAll(appMetaDataMap);
        appDataMap.putAll(appBizDataMap);
        AppPackage appPackage = new AppPackage();
        List<AppMeta> appMetaList = new ArrayList<>();
        for (String key : appDataMap.keySet()) {
            String value = appDataMap.get(key);
            List<Map<String, Object>> metaData = dao.getJdbcTemplate().queryForList(value);
            if (key.equals("platform_app") && !metaData.isEmpty()) {
                appPackage.setAppCode(metaData.get(0).get("code").toString());
                appPackage.setSourceAppId(appId);
            } else {
                if (appointMetas != null) {
                    if (appointMetas.containsKey(key)) {
                        List<Map<String, Object>> appointMetaData = pickMetaData(metaData, appointMetas.get(key));
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
            apiResult.setCode(ApiResultCode.ERROR);
            apiResult.setMsg("找不到可打包的应用");
            return apiResult;
        }
        appPackage.setAppMetaList(appMetaList);
        AppVersion av = new AppVersion();
        av.setAppId(appId);
        String packageVersion=null;
        if (StringUtils.isEmpty(version)) {
            packageVersion=generateVersionCode(appPackage.getAppCode());
        } else {
            packageVersion=version;
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

        apiResult.setData(appVersionService.createModel(av));
        return apiResult;
    }
    @RequestMapping(value = {"/packet/merge"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult packetMergeApp(String appId, String version, String description,
                               @RequestBody(required = false) Map<String,Map<String, String>> appointMetas) throws IOException {
        ApiResult apiResult = new ApiResult();
        String[] versionIds=appointMetas.keySet().toArray(new String[0]);
        List<AppPackage> appPackages=getAppointAppPackage(versionIds);
        AppPackage appPackage= mergePackage(appPackages,appointMetas);

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
        apiResult.setData(appVersionService.createModel(av));
        return apiResult;
    }

    private AppPackage mergePackage(List<AppPackage> sourceAppPackageList, Map<String, Map<String, String>> appointMetas) {
        AppPackage appPackage=new AppPackage();

        List<AppMeta> mergeAppMetaList=new ArrayList<>();
        for (AppPackage pkg:sourceAppPackageList){
            String pkgVersion=pkg.getVersion();
            appPackage.setAppCode(pkg.getAppCode());
            Map<String,String> pkgAppoints= appointMetas.get(pkgVersion);
            for (AppMeta appMeta :pkg.getAppMetaList()) {
                List<Map<String, Object>> metaData = (List<Map<String, Object>>) appMeta.getMetaData();
                String metaName=appMeta.getMetaName();
                if (pkgAppoints.containsKey(metaName)) {
                    List<Map<String, Object>> appointMetaData = pickMetaData(metaData, pkgAppoints.get(metaName));
                    AppMeta pkgAppMeta = new AppMeta(metaName, appointMetaData);
                    mergeAppMetaList.add(pkgAppMeta);
                }
            }
        }
        appPackage.setAppMetaList(distinctMergrAppMetaList(mergeAppMetaList));
        return appPackage;
    }

    private List<AppMeta> distinctMergrAppMetaList(List<AppMeta> waitMergeAppMetaList) {
        List<AppMeta> distinctAppMetaList=new ArrayList<>();
        for (AppMeta appMeta:waitMergeAppMetaList){
           AppMeta mergeAppMeta= distinctAppMetaList.stream().filter(x->x.getMetaName().equals(appMeta.getMetaName())).findFirst().orElse(null);
           if(mergeAppMeta==null){
               distinctAppMetaList.add(appMeta);
           }else {
               List<Map<String, Object>> metaData=(List<Map<String, Object>>)mergeAppMeta.getMetaData();
               for (Map<String,Object> map: (List<Map<String, Object>>)appMeta.getMetaData()){
                   String id=map.get("id").toString();
                   if(metaData.stream().noneMatch(x->x.get("id").toString().equals(id))){
                       metaData.add(map);
                   }
               }
           }
        }
        return distinctAppMetaList;
    }

    private List<AppPackage> getAppointAppPackage (String[] versions) {
        List<AppPackage> appPackageList=new ArrayList<>();
        for (String version:versions){
            AppVersion appVersion = appVersionService.getAppVersionByVersion(version);
            String appPackageData = null;
            if (appVersion != null && !StringUtils.isEmpty(appVersion.getPackagePath())) {
                try {
                    if (appVersion.getPackagePath().contains(".zgdp")) {
                        appPackageData = ZipUtils.readPackageData(appVersion.getPackagePath(), ".gdp");
                    } else {
                        Attach attach = attachService.getModel(appVersion.getPackagePath());
                        File file = downloadService.downloadFile(attach.getName(), attach.getPath());
                        appPackageData = ZipUtils.readPackageData(file, ".gdp");
                    }
                } catch (IOException ex) {
                }
                AppPackage appPackage = resolveAppPackageData(appPackageData);
                appPackageList.add(appPackage);
            }
        }
        return appPackageList;
    }

    private List<Map<String, Object>> pickMetaData(List<Map<String, Object>> metaData, String appointMetas) {
        String[] appointIds = appointMetas.split(",");
        List<Map<String, Object>> pickMetaData = new ArrayList<>();
        for (Map<String, Object> singleMetaData : metaData) {
            String metaId = singleMetaData.get("id").toString();
            if (Arrays.asList(appointIds).contains(metaId)) {
                pickMetaData.add(singleMetaData);
            }
        }
        return pickMetaData;
    }


    private String generateVersionCode(String appCode) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String dateStr = sdf.format(new Date());
        return String.format("%s_version%s", appCode, dateStr);
    }

    /*
    下载版本包
     */
    @RequestMapping(value = {"/downloadPackage/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public void downloadPackage(@PathVariable("versionId") String versionId) throws IOException {
        AppVersion appVersion = appVersionService.getModel(AppVersion.class, versionId);
        String filePath = appVersion.getPackagePath();
        File file = new File(filePath);
        FileInputStream fileInputStream = new FileInputStream(file);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        response.setContentType("application/octet-stream");
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
    @RequestMapping(value = {"/uploadPackage/{appId}"}, method = RequestMethod.POST, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult uploadPackage(@RequestParam("file") MultipartFile file, @PathVariable("appId") String appId) throws IOException {
        ApiResult apiResult = new ApiResult();
        byte[] bytes = file.getBytes();
        String targetPath = packageConfigurationProperties.getUploadPath() + file.getOriginalFilename();
        Files.write(Path.of(targetPath), bytes);
        AppVersion av = new AppVersion();
        av.setAppId(appId);
        av.setPacketTime(new Date());
        av.setPackagePath(targetPath);
        apiResult.setData(appVersionService.createModel(av));
        return apiResult;

    }

    /*
    部署版本包
     */
    @RequestMapping(value = {"/deploy/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult deployPackage(@PathVariable("versionId") String versionId) throws DaoException {
        ApiResult apiResult = new ApiResult();
        if (packageConfigurationProperties.getEnv().equals("init_source")) {
            apiResult.setMsg("本环境无法部署任何应用，请联系管理员！");
            apiResult.setCode(ApiResultCode.ERROR);
            return apiResult;
        }
        AppVersion appVersion = appVersionService.getModel(AppVersion.class, versionId);
        String appPackageData = null;
        if (appVersion != null && !StringUtils.isEmpty(appVersion.getPackagePath())) {
            try {
                if (appVersion.getPackagePath().contains(".zgdp")) {
                    appPackageData = ZipUtils.readPackageData(appVersion.getPackagePath(), ".gdp");
                    // 测试用
//                    appPackageData = ZipUtils.readPackageData("D:\\geelato-project\\app_package_temp\\upload_temp\\ob.zgdp", ".gdp");
                } else {
                    Attach attach = attachService.getModel(appVersion.getPackagePath());
                    File file = downloadService.downloadFile(attach.getName(), attach.getPath());
                    appPackageData = ZipUtils.readPackageData(file, ".gdp");
                }
            } catch (IOException ex) {
                apiResult.setMsg(ex.toString());
                apiResult.setCode(ApiResultCode.ERROR);
                return apiResult;
            }

            AppPackage appPackage = resolveAppPackageData(appPackageData);
            if (appPackage != null && !appPackage.getAppMetaList().isEmpty()) {
                try {
                    backupCurrentVersion(appVersion.getAppId());
                    deleteCurrentVersion(appVersion.getAppId());
                    deployAppPackageData(appPackage);
                    refreshApp(appVersion.getAppId());
                } catch (Exception ex) {
                    apiResult.setMsg(ex.getMessage());
                    apiResult.setCode(ApiResultCode.ERROR);
                    if (transactionStatus != null) {
                        dataSourceTransactionManager.rollback(transactionStatus);
                    }
                    return apiResult;
                }
            } else {
                apiResult.setMsg("无法读取到应用包数据，请检查应用");
                apiResult.setCode(ApiResultCode.ERROR);
                log.info("deploy error：无法读取到应用包数据，请检查应用包");
                return apiResult;
            }
        }
        apiResult.setMsg("应用部署成功！");
        return apiResult;
    }

    private void refreshApp(String appId) {
        List<EntityMeta> allEntityMeta = MetaManager.singleInstance().getAll().stream().toList();
        for (EntityMeta entityMeta : allEntityMeta) {
            if (entityMeta.getTableMeta().getAppId() != null && entityMeta.getTableMeta().getAppId().equals(appId)) {
                MetaManager.singleInstance().refreshDBMeta(entityMeta.getEntityName());
            }
        }

    }

    private void backupCurrentVersion(String appId) {
        log.info("----------------------backup version start--------------------");
        Map<String, String> appMetaMap = appMetaMap(appId, "remove");
        for (String key : appMetaMap.keySet()) {
            String value = appMetaMap.get(key);
//            log.info(String.format("remove sql：%s ",value));
//            dao.getJdbcTemplate().execute(value);
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

            log.info(String.format("remove sql：%s ", value));
            dao.getJdbcTemplate().execute(value);
        }
        log.info("----------------------delete version end--------------------");
    }


    /*
        获取打包进度
     */
    @RequestMapping(value = {"/packet/progress/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult packetProcess(@PathVariable("versionId") String appId) {
        return null;
    }


    /*
    获取部署进度
     */
    @RequestMapping(value = {"/deploy/progress/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult deployProcess(@PathVariable("versionId") String appId) {
        return null;
    }

    /*
    获取指定应用的版本信息
    */
    @RequestMapping(value = {"/queryVersion/{appId}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult queryVersion(@PathVariable("appId") String appId) {
        return null;
    }

    /*
    删除版本
    */
    @RequestMapping(value = {"/deleteVersion/{versionId}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult deleteVersion(@PathVariable("versionId") String appId) {
        return null;
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
            if (packBusData.equals("1")) {
                incrementBizMetas.add(tableName);
            }
        }
        incrementMetas.addAll(incrementBizMetas);
        return bizDataSqlMap;
    }

    private Map<String, String> appResourceMetaMap(String appId) {
        Map<String, String> map = new HashMap<>();
//        map.put("platform_resources",String.format("select * from platform_permission where app_id='%s'",appId));   //表需要加app_id
        return map;
    }

    private String writePackageData(AppVersion appVersion, AppPackage appPackage) throws IOException {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        writePackageResourceData(appPackage);
        return compressAppPackage(packageConfigurationProperties.getPath() + tempFolderPath, appVersion, appPackage);
    }

    private void writePackageResourceData(AppPackage appPackage) {
        // todo 处理打包资源文件
    }

    private String compressAppPackage(String sourcePackageFolder, AppVersion appVersion, AppPackage appPackage) throws IOException {
        String packageSuffix = ".zgdp";
        String appPackageName = StringUtils.isEmpty(appPackage.getAppCode()) ? defaultPackageName : appPackage.getAppCode();
        String appPackageFullName = (Strings.isNotBlank(appVersion.getVersion()) ? appVersion.getVersion() : appPackageName) + packageSuffix;
        String targetZipPath = packageConfigurationProperties.getPath() + appPackageFullName;
        targetZipPath = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, AttachmentSourceEnum.PLATFORM_ATTACH.getValue(), Ctx.getCurrentTenantCode(), appPackage.getSourceAppId(), appPackageFullName, true);
        ZipUtils.compressDirectory(sourcePackageFolder, targetZipPath);
        File file = new File(targetZipPath);
        Attach attach = new Attach(file);
        attach.setName(appPackageFullName);
        attach.setPath(targetZipPath);
        attach.setGenre("package");
        attach.setAppId(appPackage.getSourceAppId());
        Attach attachRst = attachService.createModel(attach);
        return attachRst.getId();
    }

    private AppPackage resolveAppPackageData(String appPackageData) {
        AppPackage appPackage = null;
        if (!StringUtils.isEmpty(appPackageData)) {
            appPackage = JSONObject.parseObject(appPackageData, AppPackage.class);
        }
        return appPackage;
    }

    private void deployAppPackageData(AppPackage appPackage) throws DaoException {
        log.info("----------------------deploy start--------------------");
        dataSourceTransactionManager = new DataSourceTransactionManager(dao.getJdbcTemplate().getDataSource());
        transactionStatus = TransactionHelper.beginTransaction(dataSourceTransactionManager);
        for (AppMeta appMeta : appPackage.getAppMetaList()) {
            log.info(String.format("开始处理元数据：%s", appMeta.getMetaName()));
            Map<String, Object> metaData = new HashMap<>();
            ArrayList<Map<String, Object>> metaDataArray = new ArrayList<>();
            String appMetaName = appMeta.getMetaName();
            Object appMetaData = appMeta.getMetaData();
            EntityMeta entityMeta = metaManager.getByEntityName(appMetaName);
            String tableName = entityMeta.getTableName();
            Boolean increment = incrementMetas.contains(tableName);
            List<String> ids = null;
            if (increment) {
                ids = incrementMetaIds.get(tableName);
            }
            JSONArray jsonArray = JSONArray.parseArray(JSONObject.toJSONString(appMetaData));
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jo = jsonArray.getJSONObject(i);
                Map<String, Object> columnMap = new HashMap<>();
                Boolean upgradeToTarget = true;
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
            List<SaveCommand> saveCommandList = jsonTextSaveParser.parseBatch(JSONObject.toJSONString(metaData), new Ctx());
            for (SaveCommand saveCommand : saveCommandList) {
                BoundSql boundSql = sqlManager.generateSaveSql(saveCommand);
                String pkValue = dao.save(boundSql);
            }
            log.info(String.format("结束处理元数据：%s", appMeta.getMetaName()));
        }
        TransactionHelper.commitTransaction(dataSourceTransactionManager, transactionStatus);
        log.info("----------------------deploy end--------------------");
    }
}
