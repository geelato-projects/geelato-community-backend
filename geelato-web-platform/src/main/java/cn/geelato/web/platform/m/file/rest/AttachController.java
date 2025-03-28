package cn.geelato.web.platform.m.file.rest;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.lang.api.NullResult;
import cn.geelato.utils.SqlParams;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentServiceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * @author diabl
 */
@ApiRestController("/attach")
@Slf4j
public class AttachController extends BaseController {
    private final FileHandler fileHandler;

    @Autowired
    public AttachController(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        Attachment attachment = fileHandler.getAttachment(id, false);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.POST)
    public ApiResult image(@PathVariable(required = true) String id) {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("pids", id);
        List<Attachment> attachmentList = fileHandler.getAttachments(queryParams);
        return ApiResult.success(attachmentList);
    }

    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    public ApiResult update(@PathVariable(required = true) String id, @RequestBody Map<String, Object> requestMap) throws InvocationTargetException, IllegalAccessException {
        Attachment attachment = fileHandler.getAttachment(id);
        BeanUtils.populate(attachment, requestMap);
        fileHandler.updateAttachment(attachment);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/replace/{sourceId}/{targetId}", method = RequestMethod.POST)
    public ApiResult replace(@PathVariable(required = true) String sourceId, @PathVariable(required = true) String targetId, @RequestBody Map<String, Object> requestMap) throws InvocationTargetException, IllegalAccessException {
        Attachment source = fileHandler.getAttachment(sourceId);
        if (source == null) {
            return ApiResult.fail("源附件不存在!");
        }
        Attachment target = fileHandler.getAttachment(targetId);
        if (target == null) {
            return ApiResult.fail("目标附件不存在!");
        }
        // 更新目标附件信息
        BeanUtils.populate(target, requestMap);
        target.setPid(source.getPid());
        target.handleGenre("Replace");
        fileHandler.updateAttachment(target);
        // 删除源附件
        fileHandler.updateId(source.getSource(), sourceId, String.valueOf(UIDGenerator.generate()), true);
        fileHandler.updateId(target.getSource(), targetId, sourceId, false);
        target.setId(sourceId);
        return ApiResult.success(target);
    }

    @RequestMapping(value = "/valid", method = RequestMethod.POST)
    public ApiResult validate(@RequestBody Map<String, Object> requestMap) {
        String attachmentIds = Objects.toString(requestMap.get("attachmentIds"), "");
        List<String> ids = StringUtils.toListDr(attachmentIds);
        if (ids == null || ids.isEmpty()) {
            return ApiResult.fail("附件ID不能为空!");
        }
        Map<String, Boolean> result = new HashMap<>();
        // 查询附件信息
        List<Attachment> attachments = fileHandler.getAttachments(SqlParams.map("ids", String.join(",", ids)));
        if (attachments == null || attachments.isEmpty()) {
            return ApiResult.fail("附件不存在!");
        }
        // 对比
        for (String id : ids) {
            boolean isExist = false;
            for (Attachment attachment : attachments) {
                if (id.equals(attachment.getId())) {
                    try {
                        File file = fileHandler.toFile(attachment);
                        isExist = file != null && file.exists();
                        if (!AttachmentServiceEnum.OSS_LOCAL.getValue().equalsIgnoreCase(attachment.getStorageType())) {
                            if (isExist) {
                                file.delete();
                            }
                        }
                    } catch (Exception ex) {
                        isExist = false;
                    }
                }
            }
            result.put(id, isExist);
        }

        return ApiResult.success(result);
    }

    @RequestMapping(value = "/storage/{type}", method = RequestMethod.POST)
    public ApiResult updateStorageType(@PathVariable(required = true) String type, @RequestBody Map<String, Object> requestMap) {
        String attachmentIds = Objects.toString(requestMap.get("attachmentIds"), "");
        List<String> ids = StringUtils.toListDr(attachmentIds);
        if (ids == null || ids.isEmpty()) {
            return ApiResult.fail("附件ID不能为空!");
        }
        // 查询附件信息
        List<Attachment> attachments = fileHandler.getAttachments(SqlParams.map("ids", String.join(",", ids)));
        if (attachments == null || attachments.isEmpty()) {
            return ApiResult.fail("附件不存在!");
        }
        Map<String, Object> result = new HashMap<>();
        for (Attachment attachment : attachments) {
            File file = fileHandler.toFile(attachment);
            if (file == null || !file.exists()) {
                result.put(attachment.getId(), "文件不存在");
                continue;
            }
            if (AttachmentServiceEnum.OSS_LOCAL.getValue().equalsIgnoreCase(type)) {
                // 阿里云OSS => 本地存储
                if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(attachment.getStorageType())) {
                    String path = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, attachment.getSource(), attachment.getTenantCode(), attachment.getAppId(), attachment.getName(), true);
                    try {
                        Files.copy(file.toPath(), Paths.get(path).normalize(), StandardCopyOption.REPLACE_EXISTING);
                        attachment.setObjectId(null);
                        attachment.setPath(path);
                        attachment.handleGenre("UpdateStorage");
                        fileHandler.updateAttachment(attachment);
                        file.delete();
                    } catch (IOException e) {
                        result.put(attachment.getId(), "文件本地化失败");
                    }
                } else {
                    result.put(attachment.getId(), "存储方式未变化");
                }
            } else if (AttachmentServiceEnum.OSS_ALI.getValue().equalsIgnoreCase(type)) {
                // 本地存储 => 阿里云OSS
                if (AttachmentServiceEnum.OSS_LOCAL.getValue().equalsIgnoreCase(attachment.getStorageType())) {
                    try {
                        Attachment target = fileHandler.uploadCloudFromLocal(attachment);
                        if (target != null) {
                            target.handleGenre("UpdateStorage");
                            fileHandler.updateAttachment(target);
                            file.delete();
                        } else {
                            result.put(attachment.getId(), "文件上传云失败");
                        }
                    } catch (IOException e) {
                        result.put(attachment.getId(), "文件上传云失败");
                    }
                } else {
                    result.put(attachment.getId(), "存储方式未变化");
                }
            }
        }
        return ApiResult.success(result);
    }

    @RequestMapping(value = "/list", method = RequestMethod.POST)
    public ApiResult list(@RequestBody Map<String, Object> requestMap) {
        List<Attachment> attachments = fileHandler.getAttachments(requestMap);
        return ApiResult.success(attachments);
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery(@RequestBody Map<String, Object> requestMap) {
        long page = NumberUtils.toLong(Objects.toString(requestMap.get("current"), ""), 1L);
        int size = NumberUtils.toInt(Objects.toString(requestMap.get("pageSize"), ""), 10);
        String orderBy = Objects.toString(requestMap.get("order"), "");
        requestMap.put("orderBy", Strings.isNotBlank(orderBy) ? orderBy.replaceAll("\\|", " ") : "create_at desc");
        requestMap.put("startNum", (page - 1) * size);
        List<Attachment> attachments = fileHandler.getAttachments(requestMap);
        long total = fileHandler.countAttachments(requestMap);
        return ApiPagedResult.success(new DataItems<>(attachments, total), page, size, size, total);
    }

    @RequestMapping(value = "/column/{type}", method = RequestMethod.GET)
    public ApiResult columnType(@PathVariable(required = true) String type) {
        List<String> typeValues = new ArrayList<>();
        if (List.of("type", "resolution").contains(type)) {
            List<Map<String, Object>> data = dao.queryForMapList("platform_attachment_query_" + type, new HashMap<>());
            if (data != null && !data.isEmpty()) {
                for (Map<String, Object> map : data) {
                    String value = map.get(type) == null ? "" : map.get(type).toString();
                    if (Strings.isNotBlank(value)) {
                        typeValues.add(value);
                    }
                }
            }
        }
        return ApiResult.success(typeValues);
    }

    @RequestMapping(value = "/remove/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> remove(@PathVariable(required = true) String id, Boolean isRemoved) {
        fileHandler.delete(id, isRemoved);
        return ApiResult.successNoResult();
    }
}
