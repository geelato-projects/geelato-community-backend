package cn.geelato.web.platform.srv.email.service;

import cn.geelato.meta.email.EmailAttachment;
import cn.geelato.meta.email.EmailMessage;
import cn.geelato.meta.UserEmailAccount;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Order;
import cn.geelato.orm.page.PageResult;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.email.MailIdCodec;
import cn.geelato.web.platform.srv.email.dto.EmailAddressDto;
import cn.geelato.web.platform.srv.email.dto.EmailAttachmentDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageDetailDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageListItemDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 邮件查询门面服务
 * <p>
 * 封装"本地优先、IMAP 兜底"策略：<br>
 * 若邮箱账号已开启同步且有本地数据，则从 PostgreSQL 查询；<br>
 * 否则降级到 {@link EmailInboxService} 直连 IMAP 服务器。
 * </p>
 */
@Service
@Slf4j
public class EmailQueryService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<EmailAddressDto>> ADDRESS_LIST_TYPE = new TypeReference<>() {};

    @Autowired
    private EmailInboxService emailInboxService;

    @Autowired
    private FileHandler fileHandler;

    // ------------------------------------------------------------------ 分页查询

    public EmailInboxService.PageResult<EmailMessageListItemDto> pageQuery(
            String userId, String emailAccountId, String folderName,
            int pageNum, int pageSize, Boolean unreadOnly) throws Exception {

        if (isSyncEnabled(userId, emailAccountId)) {
            try {
                return localPageQuery(userId, emailAccountId, folderName, pageNum, pageSize, unreadOnly);
            } catch (Exception e) {
                log.warn("本地分页查询失败，降级到 IMAP, userId={}, emailAccountId={}", userId, emailAccountId, e);
            }
        }
        return emailInboxService.pageQuery(userId, emailAccountId, folderName, pageNum, pageSize, unreadOnly);
    }

    private EmailInboxService.PageResult<EmailMessageListItemDto> localPageQuery(
            String userId, String emailAccountId, String folderName,
            int pageNum, int pageSize, Boolean unreadOnly) {

        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        if (Strings.isNotBlank(emailAccountId)) {
            filters.add(Filter.eq("emailAccountId", emailAccountId));
        }
        if (Strings.isNotBlank(folderName)) {
            filters.add(Filter.eq("folder", folderName));
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            filters.add(Filter.eq("unread", 1));
        }

        PageResult<Map<String, Object>> page = MetaFactory.query(EmailMessage.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("receivedAt"), Order.desc("uid"))
                .page(pageNum, pageSize)
                .page();

        List<Map<String, Object>> records = page.getRecords() != null ? page.getRecords() : Collections.emptyList();
        List<EmailMessageListItemDto> items = new ArrayList<>(records.size());
        for (Map<String, Object> row : records) {
            items.add(toListItemDto(row));
        }
        long total = page.getTotal();
        return new EmailInboxService.PageResult<>(items, total);
    }

    // ------------------------------------------------------------------ 邮件详情

    public EmailMessageDetailDto getMessageDetail(String userId, String mailId) throws Exception {
        if (isSyncEnabledById(userId, mailId)) {
            try {
                EmailMessageDetailDto local = localGetMessageDetail(userId, mailId);
                if (local != null) {
                    return local;
                }
            } catch (Exception e) {
                log.warn("本地获取邮件详情失败，降级到 IMAP, userId={}, mailId={}", userId, mailId, e);
            }
        }
        return emailInboxService.getMessageDetail(userId, mailId);
    }

    private EmailMessageDetailDto localGetMessageDetail(String userId, String mailId) {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);

        Map<String, Object> row = MetaFactory.query(EmailMessage.class)
                .where(
                        Filter.eq("emailAccountId", key.emailAccountId()),
                        Filter.eq("folder", key.folder()),
                        Filter.eq("uidValidity", key.uidValidity()),
                        Filter.eq("uid", key.uid()),
                        Filter.eq("delStatus", 0)
                )
                .one();

        if (row == null || row.isEmpty()) {
            return null;
        }

        String dbId = Objects.toString(row.get("id"), null);
        EmailMessageDetailDto dto = new EmailMessageDetailDto();
        dto.setId(mailId);
        dto.setEmailAccountId(key.emailAccountId());
        dto.setFolder(key.folder());
        dto.setMessageId(Objects.toString(row.get("messageId"), null));
        dto.setSubject(Objects.toString(row.get("subject"), null));
        dto.setFrom(parseAddress(Objects.toString(row.get("fromJson"), null)));
        dto.setTo(parseAddressList(Objects.toString(row.get("toJson"), null)));
        dto.setCc(parseAddressList(Objects.toString(row.get("ccJson"), null)));
        dto.setBcc(parseAddressList(Objects.toString(row.get("bccJson"), null)));
        dto.setSentAt(toDate(row.get("sentAt")));
        dto.setReceivedAt(toDate(row.get("receivedAt")));
        dto.setTextBody(Objects.toString(row.get("textBody"), null));
        dto.setHtmlBody(Objects.toString(row.get("htmlBody"), null));

        // 查询附件
        if (dbId != null) {
            List<Map<String, Object>> attRows = MetaFactory.query(EmailAttachment.class)
                    .where(Filter.eq("emailMessageId", dbId), Filter.eq("delStatus", 0))
                    .order(Order.asc("partId"))
                    .list();
            dto.setAttachments(toAttachmentDtos(attRows, mailId));
        }

        return dto;
    }

    // ------------------------------------------------------------------ 附件列表

    public List<EmailAttachmentDto> getAttachments(String userId, String mailId) throws Exception {
        if (isSyncEnabledById(userId, mailId)) {
            try {
                List<EmailAttachmentDto> local = localGetAttachments(mailId);
                if (local != null) {
                    return local;
                }
            } catch (Exception e) {
                log.warn("本地获取附件列表失败，降级到 IMAP, userId={}, mailId={}", userId, mailId, e);
            }
        }
        return emailInboxService.getAttachments(userId, mailId);
    }

    private List<EmailAttachmentDto> localGetAttachments(String mailId) {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);

        Map<String, Object> msgRow = MetaFactory.query(EmailMessage.class)
                .where(
                        Filter.eq("emailAccountId", key.emailAccountId()),
                        Filter.eq("folder", key.folder()),
                        Filter.eq("uidValidity", key.uidValidity()),
                        Filter.eq("uid", key.uid()),
                        Filter.eq("delStatus", 0)
                )
                .one();

        if (msgRow == null || msgRow.isEmpty()) {
            return null;
        }

        String dbId = Objects.toString(msgRow.get("id"), null);
        if (dbId == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> attRows = MetaFactory.query(EmailAttachment.class)
                .where(Filter.eq("emailMessageId", dbId), Filter.eq("delStatus", 0))
                .order(Order.asc("partId"))
                .list();

        return toAttachmentDtos(attRows, mailId);
    }

    // ------------------------------------------------------------------ 附件下载

    public EmailInboxService.DownloadAttachment openAttachmentStream(String userId, String mailId, String partId) throws Exception {
        if (isSyncEnabledById(userId, mailId)) {
            try {
                EmailInboxService.DownloadAttachment local = localOpenAttachmentStream(mailId, partId);
                if (local != null) {
                    return local;
                }
            } catch (Exception e) {
                log.warn("本地下载附件失败，降级到 IMAP, userId={}, mailId={}, partId={}", userId, mailId, partId, e);
            }
        }
        return emailInboxService.openAttachmentStream(userId, mailId, partId);
    }

    private EmailInboxService.DownloadAttachment localOpenAttachmentStream(String mailId, String partId) throws Exception {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);

        Map<String, Object> msgRow = MetaFactory.query(EmailMessage.class)
                .where(
                        Filter.eq("emailAccountId", key.emailAccountId()),
                        Filter.eq("folder", key.folder()),
                        Filter.eq("uidValidity", key.uidValidity()),
                        Filter.eq("uid", key.uid()),
                        Filter.eq("delStatus", 0)
                )
                .one();

        if (msgRow == null || msgRow.isEmpty()) {
            return null;
        }

        String dbId = Objects.toString(msgRow.get("id"), null);
        if (dbId == null) {
            return null;
        }

        Map<String, Object> attRow = MetaFactory.query(EmailAttachment.class)
                .where(
                        Filter.eq("emailMessageId", dbId),
                        Filter.eq("partId", partId),
                        Filter.eq("delStatus", 0)
                )
                .one();

        if (attRow == null || attRow.isEmpty()) {
            return null;
        }

        String attachmentId = Objects.toString(attRow.get("attachmentId"), null);
        if (Strings.isBlank(attachmentId)) {
            return null;
        }

        cn.geelato.meta.Attachment attachment = fileHandler.getAttachment(attachmentId);
        if (attachment == null) {
            return null;
        }

        InputStream is = fileHandler.toInputStream(attachment);
        if (is == null) {
            return null;
        }

        String fileName = Strings.isNotBlank(attachment.getName()) ? attachment.getName() : "attachment";
        String contentType = Strings.isNotBlank(attachment.getType()) ? attachment.getType() : "application/octet-stream";
        return new EmailInboxService.DownloadAttachment(fileName, contentType, is);
    }

    // ------------------------------------------------------------------ 辅助方法

    private boolean isSyncEnabled(String userId, String emailAccountId) {
        try {
            List<Filter> filters = new ArrayList<>();
            filters.add(Filter.eq("userId", userId));
            filters.add(Filter.eq("delStatus", 0));
            if (Strings.isNotBlank(emailAccountId)) {
                filters.add(Filter.eq("id", emailAccountId));
            }
            Map<String, Object> row = MetaFactory.query(UserEmailAccount.class)
                    .where(filters.toArray(new Filter[0]))
                    .one();
            return row != null && toInt(row.get("syncEnabled")) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSyncEnabledById(String userId, String mailId) {
        try {
            MailIdCodec.MailKey key = MailIdCodec.decode(mailId);
            return isSyncEnabled(userId, key.emailAccountId());
        } catch (Exception e) {
            return false;
        }
    }

    private EmailMessageListItemDto toListItemDto(Map<String, Object> row) {
        EmailMessageListItemDto dto = new EmailMessageListItemDto();
        String emailAccountId = Objects.toString(row.get("emailAccountId"), null);
        String folder = Objects.toString(row.get("folder"), null);
        Long uidValidity = toLong(row.get("uidValidity"));
        Long uid = toLong(row.get("uid"));

        // 重建 mailId 供前端使用
        if (emailAccountId != null && folder != null && uidValidity != null && uid != null) {
            dto.setId(MailIdCodec.encode(new MailIdCodec.MailKey(emailAccountId, folder, uidValidity, uid)));
        }
        dto.setEmailAccountId(emailAccountId);
        dto.setFolder(folder);
        dto.setSubject(Objects.toString(row.get("subject"), null));
        dto.setFrom(parseAddress(Objects.toString(row.get("fromJson"), null)));
        dto.setTo(parseAddressList(Objects.toString(row.get("toJson"), null)));
        dto.setCc(parseAddressList(Objects.toString(row.get("ccJson"), null)));
        dto.setSentAt(toDate(row.get("sentAt")));
        dto.setReceivedAt(toDate(row.get("receivedAt")));
        dto.setSize(toLong(row.get("size")));
        dto.setUnread(toInt(row.get("unread")) == 1);
        dto.setHasAttachments(toInt(row.get("hasAttachments")) == 1);
        dto.setSnippet(Objects.toString(row.get("snippet"), null));
        return dto;
    }

    private List<EmailAttachmentDto> toAttachmentDtos(List<Map<String, Object>> rows, String mailId) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmailAttachmentDto> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            EmailAttachmentDto dto = new EmailAttachmentDto();
            dto.setPartId(Objects.toString(row.get("partId"), null));
            dto.setFileName(Objects.toString(row.get("fileName"), null));
            dto.setContentType(Objects.toString(row.get("contentType"), null));
            dto.setSize(toLong(row.get("size")));
            dto.setInline(toInt(row.get("inline")) == 1);
            dto.setContentId(Objects.toString(row.get("contentId"), null));
            String attachmentId = Objects.toString(row.get("attachmentId"), null);
            if (Strings.isNotBlank(attachmentId)) {
                dto.setDownloadUrl("/api/resources/file?id=" + attachmentId);
            } else {
                dto.setDownloadUrl("/api/email/message/" + mailId + "/attachment/" + dto.getPartId() + "/download");
            }
            dto.setSaveToOssUrl(null);
            list.add(dto);
        }
        return list;
    }

    private EmailAddressDto parseAddress(String json) {
        if (Strings.isBlank(json)) {
            return null;
        }
        try {
            return JSON.readValue(json, EmailAddressDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<EmailAddressDto> parseAddressList(String json) {
        if (Strings.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, ADDRESS_LIST_TYPE);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private java.util.Date toDate(Object v) {
        if (v instanceof java.util.Date d) {
            return d;
        }
        return null;
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
}
