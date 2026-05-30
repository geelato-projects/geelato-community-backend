package cn.geelato.web.platform.srv.email;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Attachment;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.FileUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.email.dto.EmailFolderDto;
import cn.geelato.web.platform.srv.email.dto.EmailAttachmentDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageDetailDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageListItemDto;
import cn.geelato.web.platform.srv.email.dto.SaveEmailAttachmentRequest;
import cn.geelato.web.platform.srv.email.dto.SaveEmailAttachmentResult;
import cn.geelato.web.platform.srv.email.service.EmailInboxService;
import cn.geelato.web.platform.srv.file.param.FileParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApiRestController("/email")
@Slf4j
public class EmailInboxController extends BaseController {

    @Autowired
    private EmailInboxService emailInboxService;

    @Autowired
    private FileHandler fileHandler;

    @GetMapping("/folder/list")
    public ApiResult<List<EmailFolderDto>> listFolders(@RequestParam(value = "emailAccountId", required = false) String emailAccountId,
                                                      @RequestParam(value = "pattern", required = false) String pattern) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            log.debug("listFolders request, userId={}, emailAccountId={}, pattern={}", userId, emailAccountId, pattern);
            List<EmailFolderDto> folders = emailInboxService.listFolders(userId, emailAccountId, pattern);
            log.debug("listFolders response, userId={}, emailAccountId={}, size={}", userId, emailAccountId, folders != null ? folders.size() : 0);
            return ApiResult.success(folders);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("list email folders failed", e);
            return ApiResult.fail("获取文件夹列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/message/pageQuery")
    public ApiPagedResult<List<EmailMessageListItemDto>> pageQuery(@RequestBody(required = false) Map<String, Object> requestMap) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiPagedResult.fail("用户未登录");
            }
            Map<String, Object> rm = requestMap != null ? requestMap : java.util.Collections.emptyMap();
            String emailAccountId = Objects.toString(rm.get("emailAccountId"), null);
            String folder = Objects.toString(rm.get("folder"), null);
            Boolean unread = parseBoolean(rm.get("unread"));

            int[] page = parsePage(rm);
            log.debug("pageQuery request, userId={}, emailAccountId={}, folder={}, pageNum={}, pageSize={}, unread={}",
                    userId, emailAccountId, folder, page[0], page[1], unread);
            EmailInboxService.PageResult<EmailMessageListItemDto> result = emailInboxService.pageQuery(
                    userId,
                    emailAccountId,
                    folder,
                    page[0],
                    page[1],
                    unread
            );
            List<EmailMessageListItemDto> list = result.data();
            log.debug("pageQuery response, userId={}, emailAccountId={}, folder={}, dataSize={}, total={}",
                    userId, emailAccountId, folder, list != null ? list.size() : 0, result.total());
            return ApiPagedResult.success(list, page[0], page[1], list != null ? list.size() : 0, result.total());
        } catch (IllegalArgumentException ex) {
            return ApiPagedResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("query email list failed", e);
            return ApiPagedResult.fail("获取邮件列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/message/{id}")
    public ApiResult<EmailMessageDetailDto> getDetail(@PathVariable("id") String id) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            log.debug("getDetail request, userId={}, mailId={}", userId, id);
            EmailMessageDetailDto dto = emailInboxService.getMessageDetail(userId, id);
            log.debug("getDetail response, userId={}, mailId={}, attachments={}",
                    userId, id, dto != null && dto.getAttachments() != null ? dto.getAttachments().size() : 0);
            return ApiResult.success(dto);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("get email detail failed", e);
            return ApiResult.fail("获取邮件详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/message/{id}/attachments")
    public ApiResult<List<EmailAttachmentDto>> getAttachments(@PathVariable("id") String id) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            log.debug("getAttachments request, userId={}, mailId={}", userId, id);
            List<EmailAttachmentDto> list = emailInboxService.getAttachments(userId, id);
            log.debug("getAttachments response, userId={}, mailId={}, size={}", userId, id, list != null ? list.size() : 0);
            return ApiResult.success(list);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("get email attachments failed", e);
            return ApiResult.fail("获取附件列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/message/{id}/attachment/{partId}/download")
    public void downloadAttachment(@PathVariable("id") String id, @PathVariable("partId") String partId) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                response.setStatus(401);
                return;
            }
            log.debug("downloadAttachment request, userId={}, mailId={}, partId={}", userId, id, partId);
            EmailInboxService.DownloadAttachment da = emailInboxService.openAttachmentStream(userId, id, partId);
            response.setContentType(da.contentType());
            String fileName = Strings.isNotBlank(da.fileName()) ? da.fileName() : "attachment";
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            try (InputStream is = da.inputStream(); OutputStream os = response.getOutputStream()) {
                copy(is, os);
            }
            log.debug("downloadAttachment done, userId={}, mailId={}, partId={}, fileName={}", userId, id, partId, fileName);
        } catch (IllegalArgumentException ex) {
            try {
                response.setStatus(400);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.error("download email attachment failed", e);
            try {
                response.setStatus(500);
            } catch (Exception ignored) {
            }
        }
    }

    @PostMapping("/message/{id}/attachment/{partId}/save")
    public ApiResult<SaveEmailAttachmentResult> saveAttachment(@PathVariable("id") String id, @PathVariable("partId") String partId, @RequestBody(required = false) SaveEmailAttachmentRequest req) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }

            SaveEmailAttachmentRequest r = req != null ? req : new SaveEmailAttachmentRequest();
            String serviceType = Strings.isNotBlank(r.getServiceType()) ? r.getServiceType() : "ALIYUN";
            String sourceType = Strings.isNotBlank(r.getSourceType()) ? r.getSourceType() : "email";
            String objectId = Strings.isNotBlank(r.getObjectId()) ? r.getObjectId() : id;
            String appId = Strings.isNotBlank(r.getAppId()) ? r.getAppId() : getAppId();
            String tenantCode = Strings.isNotBlank(r.getTenantCode()) ? r.getTenantCode() : getTenantCode();

            log.debug("saveAttachment request, tenantCode={}, userId={}, mailId={}, partId={}, serviceType={}, sourceType={}, objectId={}, appId={}",
                    tenantCode, userId, id, partId, serviceType, sourceType, objectId, appId);
            EmailInboxService.DownloadAttachment da = emailInboxService.openAttachmentStream(userId, id, partId);
            String fileName = Strings.isNotBlank(da.fileName()) ? da.fileName() : ("attachment-" + partId);
            String fileExt = FileUtils.getFileExtension(fileName);

            File tempFile = null;
            try (InputStream is = da.inputStream()) {
                tempFile = FileUtils.createTempFile(is, fileExt);
                FileParam fileParam = new FileParam();
                fileParam.setServiceType(serviceType);
                fileParam.setSourceType(sourceType);
                fileParam.setObjectId(objectId);
                fileParam.setAppId(appId);
                fileParam.setTenantCode(tenantCode);

                Attachment attachment = fileHandler.upload(tempFile, fileName, fileParam);
                if (attachment == null || Strings.isBlank(attachment.getId())) {
                    return ApiResult.fail("附件保存失败");
                }

                SaveEmailAttachmentResult result = new SaveEmailAttachmentResult();
                result.setAttachmentId(attachment.getId());
                result.setName(attachment.getName());
                result.setSize(attachment.getSize());
                result.setContentType(attachment.getType());
                result.setDownloadUrl("/api/resources/file?id=" + attachment.getId());
                log.debug("saveAttachment done, tenantCode={}, userId={}, mailId={}, partId={}, attachmentId={}, fileName={}",
                        tenantCode, userId, id, partId, attachment.getId(), fileName);
                return ApiResult.success(result);
            } finally {
                if (tempFile != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(tempFile.toPath());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("save email attachment failed", e);
            return ApiResult.fail("附件保存失败: " + e.getMessage());
        }
    }

    private static void copy(InputStream is, OutputStream os) throws java.io.IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) >= 0) {
            os.write(buf, 0, len);
        }
        os.flush();
    }

    private static Boolean parseBoolean(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static int[] parsePage(Map<String, Object> requestMap) {
        if (requestMap == null || requestMap.isEmpty()) {
            return new int[]{1, 20};
        }
        String p = Objects.toString(requestMap.get("@p"), "");
        if (Strings.isNotBlank(p) && p.contains(",")) {
            String[] ps = p.split(",", 2);
            try {
                int pageNum = Integer.parseInt(ps[0].trim());
                int pageSize = Integer.parseInt(ps[1].trim());
                return new int[]{Math.max(1, pageNum), Math.max(1, pageSize)};
            } catch (Exception ignored) {
            }
        }

        String current = Objects.toString(requestMap.get("current"), Objects.toString(requestMap.get("page"), ""));
        String size = Objects.toString(requestMap.get("pageSize"), Objects.toString(requestMap.get("size"), ""));
        int pageNum = 1;
        int pageSize = 20;
        try {
            if (Strings.isNotBlank(current)) {
                pageNum = Integer.parseInt(current);
            }
            if (Strings.isNotBlank(size)) {
                pageSize = Integer.parseInt(size);
            }
        } catch (Exception ignored) {
        }
        return new int[]{Math.max(1, pageNum), Math.max(1, pageSize)};
    }
}

