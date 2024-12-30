package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.Attachment;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
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
    public ApiResult uploadFile(@RequestParam("file") MultipartFile file, String serviceType, String tableType, String genre, String root, boolean isThumbnail, Boolean isRename, Integer dimension, String appId, String tenantCode) throws IOException {
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("File is empty");
        }
        tenantCode = Strings.isBlank(tenantCode) ? SessionCtx.getCurrentTenantCode() : tenantCode;
        Attachment attachment = fileHandler.upload(file, serviceType, tableType, genre, root, isThumbnail, isRename, dimension, appId, tenantCode);
        if (attachment == null) {
            return ApiResult.fail("Upload failed");
        }
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/object", method = RequestMethod.POST)
    public ApiResult uploadObject(@RequestBody Map<String, Object> params, String fileName, String catalog) throws IOException {
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
            // 路径
            String rootDir = UploadService.ROOT_CONFIG_DIRECTORY;
            if (Strings.isNotBlank(catalog)) {
                rootDir = String.format(catalog.startsWith("/") ? "%s%s" : "%s/%s", rootDir, catalog);
            }
            UploadService.fileMkdirs(rootDir);
            // 文件
            File file = new File(String.format("%s/%s", rootDir, fileName));
            if (file.exists() && !UploadService.fileResetName(file)) {
                file.delete();
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
    public ApiResult uploadJson(@RequestBody String JsonData, String fileName, String catalog) throws IOException {
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
            // 路径
            String rootDir = UploadService.ROOT_CONFIG_DIRECTORY;
            if (Strings.isNotBlank(catalog)) {
                rootDir = String.format(catalog.startsWith("/") ? "%s%s" : "%s/%s", rootDir, catalog);
            }
            UploadService.fileMkdirs(rootDir);
            // 文件
            File file = new File(String.format("%s/%s", rootDir, fileName));
            if (file.exists() && !UploadService.fileResetName(file)) {
                file.delete();
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
    public ApiResult uploadModel(@PathVariable("entityName") String entityName, @PathVariable("id") String id, String fileName) {
        if (Strings.isBlank(entityName)) {
            return ApiResult.fail("Entity Name Is Null");
        }
        if (Strings.isBlank(id)) {
            return ApiResult.fail("Entity ID Is Null");
        }
        if (Strings.isBlank(fileName)) {
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
            for (Map.Entry<String, Object> columnEntry : columnMap.entrySet()) {
                if (columnEntry.getValue() == null) {
                    columnEntry.setValue("");
                }
            }
            /*String configName = null;
            String tenantCode = columnMap.get("tenantCode") == null ? null : String.valueOf(columnMap.get("tenantCode"));
            if (Strings.isBlank(tenantCode)) {
                return result.error().setMsg("TenantCode Is Null");
            }
            String appId = columnMap.get("appId") == null ? null : String.valueOf(columnMap.get("appId"));
            if (Strings.isBlank(appId)) {
                configName = String.format("%s_%s%s", tenantCode, id, ROOT_CONFIG_SUFFIX);
            } else {
                configName = String.format("%s_%s_%s%s", tenantCode, appId, id, ROOT_CONFIG_SUFFIX);
            }*/
            return uploadJson(JSON.toJSONString(columnMap), fileName, "");
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

