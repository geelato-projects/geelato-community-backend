package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.FileUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.enums.TimeUnitEnum;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("/upload")
@Slf4j
public class UploadController extends BaseController {
    private final DevTableColumnService devTableColumnService;
    private final FileHandler fileHandler;

    @Autowired
    public UploadController(DevTableColumnService devTableColumnService, FileHandler fileHandler) {
        this.devTableColumnService = devTableColumnService;
        this.fileHandler = fileHandler;
    }

    /**
     * 上传文件接口
     *
     * @param file        上传的文件
     * @param serviceType 文件服务器类型
     * @param tableType   表类型
     * @param genre       文件类型
     * @param root        文件根目录
     * @param isThumbnail 是否生成缩略图
     * @param isRename    是否重命名文件
     * @param dimension   缩略图尺寸
     * @param appId       应用ID
     * @param tenantCode  租户编码
     * @return ApiResult 对象，包含操作结果和返回数据
     */
    @RequestMapping(value = "/file", method = RequestMethod.POST)
    public ApiResult<?> uploadFile(@RequestParam("file") MultipartFile file,
                                   String serviceType, String tableType,
                                   String root, Boolean isRename,
                                   String objectId, String formIds,
                                   String genre, String batchNo,
                                   String invalidTime, Integer validDuration,
                                   Boolean isThumbnail, Boolean onlyThumb, String dimension, String thumbScale,
                                   String appId, String tenantCode) throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("File is empty");
        }
        // 参数校验和赋值
        appId = Strings.isBlank(appId) ? getAppId() : appId;
        tenantCode = Strings.isBlank(tenantCode) ? SessionCtx.getCurrentTenantCode() : tenantCode;
        isRename = isRename == null || isRename;
        // 生成文件路径
        String path;
        if (Strings.isNotBlank(root)) {
            path = UploadService.getSaveRootPath(root, file.getOriginalFilename(), isRename);
        } else {
            path = UploadService.getRootSavePath(tableType, tenantCode, appId, file.getOriginalFilename(), isRename);
        }
        // 处理有效期参数，如果设置了有效时长则计算失效时间
        Date invalidDate = DateUtils.parse(invalidTime, DateUtils.DATETIME);
        if (validDuration != null && validDuration > 0) {
            invalidDate = DateUtils.calculateTime(validDuration.toString(), TimeUnitEnum.SECOND.name());
        }
        // 构建文件参数对象
        FileParam fileParam = FileParamUtils.by(serviceType, tableType, objectId, formIds, genre, invalidDate, batchNo, appId, tenantCode, isThumbnail, onlyThumb, dimension, thumbScale);
        // 上传文件并获取附件信息
        Attachment attachment = fileHandler.upload(file, path, fileParam);
        attachment.setSource(tableType);
        attachment.setStorageType(serviceType);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/object", method = RequestMethod.POST)
    public ApiResult<?> uploadObject(@RequestBody Map<String, Object> params, String fileName, String catalog) throws IOException {
        if (params == null || params.isEmpty()) {
            return ApiResult.fail("Params is empty");
        }
        if (Strings.isBlank(fileName)) {
            return ApiResult.fail("File name is empty");
        }
        FileOutputStream fops = null;
        ObjectOutputStream oops = null;
        try {
            // 文件名称
            String ext = FileUtils.getFileExtension(fileName);
            if (Strings.isBlank(ext) || !ext.equalsIgnoreCase(UploadService.ROOT_CONFIG_SUFFIX)) {
                fileName += UploadService.ROOT_CONFIG_SUFFIX;
            }
            // 获取基础配置目录
            Path rootPath = Paths.get(UploadService.getRootConfigDirectory());
            // 添加目录路径（如果存在）
            if (Strings.isNotBlank(catalog)) {
                rootPath = rootPath.resolve(catalog.startsWith("/") ? catalog.substring(1) : catalog);
            }
            // 创建目录（包括所有不存在的父目录）
            Files.createDirectories(rootPath);
            // 构建文件
            File file = rootPath.resolve(fileName).toFile();
            // 如果文件存在，则重命名文件
            if (file.exists() && !UploadService.fileResetName(file)) {
                Files.deleteIfExists(file.toPath());
            }
            fops = new FileOutputStream(file);
            oops = new ObjectOutputStream(fops);
            oops.writeObject(params);
            return ApiResult.success(file.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        } finally {
            if (oops != null) {
                oops.close();
            }
            if (fops != null) {
                fops.close();
            }
        }
    }

    @RequestMapping(value = "/json", method = RequestMethod.POST)
    public ApiResult<?> uploadJson(@RequestBody String JsonData, String fileName, String catalog) throws IOException {
        if (Strings.isBlank(JsonData)) {
            return ApiResult.fail("Json data is empty");
        }
        if (Strings.isBlank(fileName)) {
            return ApiResult.fail("File name is empty");
        }

        FileWriter fileWriter = null;
        BufferedWriter bufferedWriter = null;
        try {
            // 文件名称
            String ext = FileUtils.getFileExtension(fileName);
            if (Strings.isBlank(ext) || !ext.equalsIgnoreCase(UploadService.ROOT_CONFIG_SUFFIX)) {
                fileName += UploadService.ROOT_CONFIG_SUFFIX;
            }
            // 获取基础配置目录
            Path rootPath = Paths.get(UploadService.getRootConfigDirectory());
            // 添加目录路径（如果存在）
            if (Strings.isNotBlank(catalog)) {
                rootPath = rootPath.resolve(catalog.startsWith("/") ? catalog.substring(1) : catalog);
            }
            // 创建目录（包括所有不存在的父目录）
            Files.createDirectories(rootPath);
            // 构建文件
            File file = rootPath.resolve(fileName).toFile();
            // 如果文件存在，则重命名文件
            if (file.exists() && !UploadService.fileResetName(file)) {
                Files.deleteIfExists(file.toPath());
            }
            fileWriter = new FileWriter(file);
            bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(JsonData);
            return ApiResult.success(file.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        } finally {
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    @RequestMapping(value = "/model/{entityName}/{id}", method = RequestMethod.POST)
    public ApiResult<?> uploadModel(@PathVariable(value = "entityName", required = true) String entityName,
                                    @PathVariable(value = "id", required = true) String id, String fileName) {
        List<String> fileNames = StringUtils.toListDr(fileName);
        if (fileNames.isEmpty()) {
            return ApiResult.fail("File Name Is Null");
        }
        try {
            String fieldNames = getColumnFieldNames(entityName);
            if (Strings.isBlank(fieldNames)) {
                return ApiResult.fail("Column Meta Is Null");
            }
            String sql = String.format("select %s from %s where id = '%s'", fieldNames, entityName, id);
            Map<String, Object> columnMap = dao.getJdbcTemplate().queryForMap(sql);
            if (columnMap.isEmpty()) {
                return ApiResult.fail("Entity Query Is Null");
            }
            // 微信信息字段特殊处理，不保存unSaveConfig字段中定义的字段
            List<String> unSaveConfig = new ArrayList<>();
            if (columnMap.get("unSaveConfig") != null) {
                unSaveConfig = StringUtils.toListDr(columnMap.get("unSaveConfig").toString());
            }
            // 模型默认字段，不添加
            List<ColumnMeta> metaList = MetaManager.singleInstance().getDefaultColumn();
            List<String> defaultColumnNames = metaList.stream()
                    .map(ColumnMeta::getFieldName)
                    .filter(fieldName -> !"tenantCode".equals(fieldName))
                    .toList();
            // 遍历字段
            Map<String, Object> columnMapNew = new HashMap<>();
            for (Map.Entry<String, Object> columnEntry : columnMap.entrySet()) {
                // 不保存的字段，跳过
                if (unSaveConfig.contains(columnEntry.getKey()) || defaultColumnNames.contains(columnEntry.getKey())) {
                    continue;
                }
                columnMapNew.put(columnEntry.getKey(), columnEntry.getValue() == null ? "" : columnEntry.getValue());
            }
            for (String name : fileNames) {
                uploadJson(JSON.toJSONString(columnMapNew), name, "");
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private String getColumnFieldNames(String tableName) {
        String fieldName = null;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            List<ColumnMeta> columnMetas = devTableColumnService.queryModel(ColumnMeta.class, params);
            if (columnMetas != null && !columnMetas.isEmpty()) {
                Set<String> fields = new LinkedHashSet<>();
                for (ColumnMeta meta : columnMetas) {
                    fields.add(String.format("%s %s", meta.getName(), meta.getFieldName()));
                }
                fieldName = String.join(",", fields);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return fieldName;
    }
}

