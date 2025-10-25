package cn.geelato.web.platform.srv.file;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.lang.api.NullResult;
import cn.geelato.utils.*;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.base.service.UploadService;
import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.srv.file.enums.AttachmentServiceEnum;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.srv.file.enums.FileGenreEnum;
import cn.geelato.web.platform.srv.file.param.FileParam;
import cn.geelato.web.platform.utils.FileParamUtils;
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
import java.util.stream.Collectors;

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

    /**
     * 复制附件
     * 复制一份新的文件。Copy
     *
     * @param id 要复制的附件ID
     * @return 新的附件对象
     */
    @RequestMapping(value = "/copy/{id}", method = RequestMethod.POST)
    public ApiResult<?> copy(@PathVariable(required = true) String id, @RequestBody Map<String, Object> requestMap) throws IOException {
        String genre = (String) requestMap.get("genre");
        String name = (String) requestMap.get("name");
        Date invalidTime = DateUtils.parse((String) requestMap.get("invalidTime"), DateUtils.DATETIME);
        log.info("copy attachment id: {}, genre: {}, name: {}, invalidTime: {}", id, genre, name, invalidTime);
        // 1. 查询
        Attachment attachment = fileHandler.getAttachment(id);
        // 2. 检查是否存在
        if (attachment == null) {
            return ApiResult.fail("附件不存在!");
        }
        // 3. 找到文件
        File file = fileHandler.toFile(attachment);
        if (file == null || !file.exists()) {
            return ApiResult.fail("文件不存在!");
        }
        // 3.5,文件名称处理
        if (StringUtils.isNotBlank(name)) {
            name = String.format("%s.%s", FileUtils.getFileName(name), FileUtils.getFileExtensionWithNoDot(attachment.getName()));
        } else {
            name = attachment.getName();
        }
        // 4. 上传文件
        FileParam fileParam = FileParamUtils.by(attachment.getStorageType(), attachment.getSource(), null, null, attachment.getGenre(), invalidTime, null, attachment.getAppId(), attachment.getTenantCode(), null, null, null, null);
        Attachment copyAttachment = fileHandler.upload(file, StringUtils.isBlank(name) ? attachment.getName() : name, fileParam);
        // 5. 处理附件属性
        copyAttachment.handleGenre(genre, FileGenreEnum.Copy.name());
        copyAttachment.setPid(attachment.getPid());
        copyAttachment.setResolution(attachment.getResolution());
        copyAttachment.setSource(attachment.getSource());
        copyAttachment.setStorageType(attachment.getStorageType());
        fileHandler.updateAttachment(copyAttachment);
        return ApiResult.success(copyAttachment);
    }

    /**
     * 引用附件,返回新的附件对象
     * 引用一份已存在的文件，路径一致。Quote，pid
     *
     * @param id 要引用的附件ID
     * @return 新的附件对象
     */
    @RequestMapping(value = "/quote/{id}", method = RequestMethod.POST)
    public ApiResult<?> quote(@PathVariable String id, @RequestBody Map<String, Object> requestMap) {
        String genre = (String) requestMap.get("genre");
        String name = (String) requestMap.get("name");
        Date invalidTime = DateUtils.parse((String) requestMap.get("invalidTime"), DateUtils.DATETIME);
        log.info("quote attachment id: {}, genre: {}, name: {}, invalidTime: {}", id, genre, name, invalidTime);
        // 1. 查询
        Attachment attachment = fileHandler.getAttachment(id);
        // 2. 检查是否存在
        if (attachment == null) {
            return ApiResult.fail("附件不存在!");
        }
        // 2.5,文件名称处理
        if (StringUtils.isNotBlank(name)) {
            name = String.format("%s.%s", FileUtils.getFileName(name), FileUtils.getFileExtensionWithNoDot(attachment.getName()));
        } else {
            name = attachment.getName();
        }
        // 3. 检查是否已引用
        if (attachment.getGenre() == null || !attachment.getGenre().contains(FileGenreEnum.Quote.name())) {
            attachment.setPid(attachment.getId());
        }
        attachment.handleGenre(genre, FileGenreEnum.Quote.name());
        attachment.setName(StringUtils.isBlank(name) ? attachment.getName() : name);
        attachment.setInvalidTime(invalidTime);
        attachment.setId(null);
        Attachment quoteAttachment = fileHandler.createAttachment(attachment);
        return ApiResult.success(quoteAttachment);
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<?> get(@PathVariable String id) {
        Attachment attachment = fileHandler.getAttachment(id, false);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.POST)
    public ApiResult<?> image(@PathVariable(required = true) String id) {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("pids", id);
        List<Attachment> attachmentList = fileHandler.getAttachments(queryParams);
        return ApiResult.success(attachmentList);
    }

    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    public ApiResult<?> update(@PathVariable String id, @RequestBody Map<String, Object> requestMap) throws InvocationTargetException, IllegalAccessException {
        Attachment attachment = fileHandler.getAttachment(id);
        BeanUtils.populate(attachment, requestMap);
        fileHandler.updateAttachment(attachment);
        return ApiResult.success(attachment);
    }

    @RequestMapping(value = "/replace/{sourceId}/{targetId}", method = RequestMethod.POST)
    public ApiResult<?> replace(@PathVariable String sourceId, @PathVariable(required = true) String targetId, @RequestBody Map<String, Object> requestMap) throws InvocationTargetException, IllegalAccessException {
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
        target.handleGenre(FileGenreEnum.Replace.name());
        fileHandler.updateAttachment(target);
        // 删除源附件
        fileHandler.updateId(source.getSource(), sourceId, String.valueOf(UIDGenerator.generate()), true);
        fileHandler.updateId(target.getSource(), targetId, sourceId, false);
        target.setId(sourceId);
        return ApiResult.success(target);
    }

    @RequestMapping(value = "/valid", method = RequestMethod.POST)
    public ApiResult<?> validate(@RequestBody Map<String, Object> requestMap) {
        String attachmentIds = Objects.toString(requestMap.get("attachmentIds"), "");
        List<String> ids = StringUtils.toListDr(attachmentIds);
        if (ids.isEmpty()) {
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
                        if (!AttachmentServiceEnum.LOCAL.getValue().equalsIgnoreCase(attachment.getStorageType())) {
                            if (isExist) {
                                Files.deleteIfExists(file.toPath());
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
    public ApiResult<?> updateStorageType(@PathVariable String type, @RequestBody Map<String, Object> requestMap) throws IOException {
        String attachmentIds = Objects.toString(requestMap.get("attachmentIds"), "");
        List<String> ids = StringUtils.toListDr(attachmentIds);
        if (ids.isEmpty()) {
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
            if (AttachmentServiceEnum.LOCAL.getValue().equalsIgnoreCase(type)) {
                // 阿里云OSS => 本地存储
                if (AttachmentServiceEnum.ALIYUN.getValue().equalsIgnoreCase(attachment.getStorageType())) {
                    String path = UploadService.getRootSavePath(attachment.getSource(), attachment.getTenantCode(), attachment.getAppId(), attachment.getName(), true);
                    try {
                        Files.copy(file.toPath(), Paths.get(path).normalize(), StandardCopyOption.REPLACE_EXISTING);
                        attachment.setObjectId(null);
                        attachment.setPath(path);
                        attachment.handleGenre(FileGenreEnum.UpdateStorage.name());
                        fileHandler.updateAttachment(attachment);
                        Files.deleteIfExists(file.toPath());
                    } catch (IOException e) {
                        result.put(attachment.getId(), "文件本地化失败");
                    }
                } else {
                    result.put(attachment.getId(), "存储方式未变化");
                }
            } else if (AttachmentServiceEnum.ALIYUN.getValue().equalsIgnoreCase(type)) {
                // 本地存储 => 阿里云OSS
                if (AttachmentServiceEnum.LOCAL.getValue().equalsIgnoreCase(attachment.getStorageType())) {
                    try {
                        Attachment target = fileHandler.uploadCloudFromLocal(attachment);
                        if (target != null) {
                            target.handleGenre(FileGenreEnum.UpdateStorage.name());
                            fileHandler.updateAttachment(target);
                            Files.deleteIfExists(file.toPath());
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
    public ApiResult<?> list(@RequestBody Map<String, Object> requestMap) {
        List<Attachment> attachments = fileHandler.getAttachments(requestMap);
        return ApiResult.success(attachments);
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery(@RequestBody Map<String, Object> requestMap) {
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
    public ApiResult<?> columnType(@PathVariable String type) {
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
    public ApiResult<NullResult> remove(@PathVariable String id, Boolean isRemoved) {
        fileHandler.delete(id, isRemoved);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/remove/{type}", method = RequestMethod.POST)
    public ApiResult<NullResult> batchRemove(@PathVariable String type, Boolean isRemoved, @RequestBody Map<String, Object> requestMap) {
        // 附件id集合处理
        List<String> attachmentIds = listStream(StringUtils.toListDr(Objects.toString(requestMap.get("attachmentIds"), "")));
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return ApiResult.fail("附件ID不能为空!");
        }
        // 按批次号查询附件
        if ("batchNo".equalsIgnoreCase(type)) {
            // 按主键集合获取附件
            List<Attachment> byIds = fileHandler.getAttachments(SqlParams.map("ids", String.join(",", attachmentIds)));
            if (byIds != null && !byIds.isEmpty()) {
                // 获取批次号集合
                List<String> batchNos = listStream(byIds.stream().map(Attachment::getBatchNo).collect(Collectors.toList()));
                if (batchNos == null || batchNos.isEmpty()) {
                    throw new RuntimeException("附件批次号不存在!");
                }
                List<Attachment> byBatchNos = fileHandler.getAttachments(SqlParams.map("batchNo", String.join(",", batchNos)));
                if (byBatchNos != null && !byBatchNos.isEmpty()) {
                    attachmentIds = listStream(byBatchNos.stream().map(Attachment::getId).collect(Collectors.toList()));
                }
            }
        }
        for (String id : attachmentIds) {
            fileHandler.delete(id, isRemoved);
        }
        return ApiResult.successNoResult();
    }

    /**
     * 将附件转换为PDF
     * 支持Excel文件（.xlsx, .xls）转换为PDF格式
     *
     * @param id 附件ID
     * @return 转换后的PDF附件ID
     */
    @RequestMapping(value = "/toPdf/{id}", method = RequestMethod.GET)
    public ApiResult<?> toPdf(@PathVariable String id) {
        try {
            log.info("Converting attachment to PDF, id: {}", id);
            
            // 1. 获取原始附件信息
            Attachment attachment = fileHandler.getAttachment(id, false);
            if (attachment == null) {
                return ApiResult.fail("附件不存在!");
            }
            
            // 2. 获取文件
            File file = fileHandler.toFile(id);
            if (file == null || !file.exists()) {
                return ApiResult.fail("文件不存在!");
            }
            
            // 3. 验证文件格式
            String fileName = attachment.getName();
            if (!fileName.toLowerCase().endsWith(".xlsx") && !fileName.toLowerCase().endsWith(".xls")) {
                return ApiResult.fail("仅支持Excel文件(.xlsx, .xls)转换为PDF!");
            }
            
            // 4. 转换为PDF
            Attachment pdfAttachment = fileHandler.toPdf(AttachmentSourceEnum.ATTACH.getValue(), attachment);
            if (pdfAttachment == null) {
                return ApiResult.fail("PDF转换失败!");
            }
            
            log.info("Successfully converted attachment {} to PDF: {}", id, pdfAttachment.getId());
            return ApiResult.success(pdfAttachment.getId());
            
        } catch (Exception e) {
            log.error("Failed to convert attachment {} to PDF: {}", id, e.getMessage(), e);
            return ApiResult.fail("PDF转换失败: " + e.getMessage());
        }
    }

    private List<String> listStream(List<String> list) {
        return list == null ? new ArrayList<>() : list.stream()
                .filter(Objects::nonNull) // 去null
                .map(String::trim)      // 去除字符串两端的空白
                .filter(s -> !s.isBlank()) // 去空字符串
                .distinct()             // 去重
                .collect(Collectors.toList());
    }
}
