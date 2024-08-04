package cn.geelato.web.platform.m.base.rest;

import cn.geelato.web.platform.m.base.entity.Resources;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.ResourcesService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.model.service.DevTableColumnService;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.base.entity.Attach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author diabl
 * @date 2023/7/4 10:46
 */
@Controller
@RequestMapping(value = "/api/upload")
public class UploadController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(UploadController.class);
    @Autowired
    private AttachService attachService;
    @Autowired
    private ResourcesService resourcesService;
    @Autowired
    private DevTableColumnService devTableColumnService;

    @RequestMapping(value = "/file", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult uploadFile(@RequestParam("file") MultipartFile file, HttpServletRequest request, Boolean isRename, String tableType, String objectId, String genre, String root, String appId, String tenantCode) {
        ApiResult result = new ApiResult();
        if (file == null || file.isEmpty()) {
            return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        try {
            Attach attach = new Attach(file);
            attach.setObjectId(objectId);
            attach.setGenre(genre);
            attach.setAppId(appId);
            if (Strings.isNotBlank(root)) {
                attach.setPath(UploadService.getSaveRootPath(root, attach.getName(), true));
            } else {
                // upload/存放表/租户编码/应用Id
                attach.setPath(UploadService.getSavePath(UploadService.ROOT_DIRECTORY, tableType, tenantCode, appId, attach.getName(), true));
            }
            byte[] bytes = file.getBytes();
            Files.write(Paths.get(attach.getPath()), bytes);
            // 资源存资源表
            if (AttachmentSourceEnum.PLATFORM_RESOURCES.getValue().equalsIgnoreCase(tableType)) {
                Resources target = UploadService.copyProperties(attach, Resources.class);
                result.setData(resourcesService.createModel(target));
            } else {
                result.setData(attachService.createModel(attach));
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/object", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult uploadObject(@RequestBody Map<String, Object> params, String fileName, String catalog) throws IOException {
        ApiResult result = new ApiResult();
        if (params == null || params.isEmpty() || Strings.isBlank(fileName)) {
            return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }
        FileOutputStream fops = null;
        ObjectOutputStream oops = null;
        try {
            // 文件名称
            String ext = UploadService.getFileExtension(fileName);
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
            result.setData(file.getName());
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        } finally {
            if (oops != null) {
                oops.close();
            }
            if (fops != null) {
                fops.close();
            }
        }

        return result;
    }

    @RequestMapping(value = "/json", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult uploadJson(@RequestBody String JsonData, String fileName, String catalog) throws IOException {
        ApiResult result = new ApiResult();
        if (Strings.isBlank(JsonData) || Strings.isBlank(fileName)) {
            return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }
        FileWriter fileWriter = null;
        BufferedWriter bufferedWriter = null;
        try {
            // 文件名称
            String ext = UploadService.getFileExtension(fileName);
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
            result.setData(file.getName());
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        } finally {
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (fileWriter != null) {
                fileWriter.close();
            }
        }

        return result;
    }

    @RequestMapping(value = "/model/{entityName}/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult uploadModel(@PathVariable("entityName") String entityName, @PathVariable("id") String id, String fileName) {
        ApiResult result = new ApiResult();
        if (Strings.isBlank(entityName) || Strings.isBlank(id)) {
            return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }
        if (Strings.isBlank(fileName)) {
            return result.error().setMsg("File Name Is Null");
        }
        try {
            String fieldNames = getColumnFieldNames(entityName);
            if (Strings.isBlank(fieldNames)) {
                return result.error().setMsg("Column Meta Is Null");
            }
            String sql = String.format("select %s from %s where id = '%s'", fieldNames, entityName, id);
            Map<String, Object> columnMap = dao.getJdbcTemplate().queryForMap(sql);
            if (columnMap == null || columnMap.isEmpty()) {
                return result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
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
            result = uploadJson(JSON.toJSONString(columnMap), fileName, "");
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(e.getMessage());
        }

        return result;
    }

    private String getColumnFieldNames(String tableName) {
        String fieldName = null;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            // params.put("enableStatus", EnableStatusEnum.ENABLED.getCode());
            List<ColumnMeta> columnMetas = devTableColumnService.queryModel(ColumnMeta.class, params);
            if (columnMetas != null && columnMetas.size() > 0) {
                Set<String> fields = new LinkedHashSet<>();
                for (ColumnMeta meta : columnMetas) {
                    fields.add(String.format("%s %s", meta.getName(), meta.getFieldName()));
                }
                fieldName = String.join(",", fields);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return fieldName;
    }
}

