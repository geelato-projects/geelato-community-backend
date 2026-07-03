package cn.geelato.web.platform.srv.email.service;

import cn.geelato.meta.UserEmailDraft;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import cn.geelato.web.platform.srv.email.dto.EmailAddressDto;
import cn.geelato.web.platform.srv.email.dto.EmailDraftDto;
import cn.geelato.web.platform.srv.email.dto.EmailDraftUpsertRequest;
import cn.geelato.web.platform.srv.email.dto.MailAttachmentRefDto;
import cn.geelato.web.platform.srv.email.dto.SendEmailRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EmailDraftService {
    public record PageResult<T>(List<T> data, long total) {
    }

    public PageResult<EmailDraftDto> pageQuery(String userId, String emailAccountId, String keyword, int pageNum, int pageSize) {
        List<Map<String, Object>> rows = MetaFactory.query(UserEmailDraft.class)
                .where(buildFilters(userId, emailAccountId))
                .order(Order.desc("updateAt"), Order.desc("createAt"))
                .list();
        List<EmailDraftDto> all = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            EmailDraftDto dto = toDto(row);
            if (matchesKeyword(dto, keyword)) {
                all.add(dto);
            }
        }
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, pageSize);
        int offset = (safePageNum - 1) * safePageSize;
        if (offset >= all.size()) {
            return new PageResult<>(Collections.emptyList(), all.size());
        }
        int to = Math.min(offset + safePageSize, all.size());
        return new PageResult<>(all.subList(offset, to), all.size());
    }

    public EmailDraftDto get(String userId, String id) {
        Map<String, Object> row = MetaFactory.query(UserEmailDraft.class)
                .where(
                        Filter.eq("id", id),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .one();
        return row == null || row.isEmpty() ? null : toDto(row);
    }

    public String createOrUpdate(String userId, String tenantCode, EmailDraftUpsertRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (Strings.isBlank(req.getEmailAccountId())) {
            throw new IllegalArgumentException("邮箱账号不能为空");
        }
        String bodyType = Strings.isNotBlank(req.getBodyType()) ? req.getBodyType() : (Strings.isNotBlank(req.getHtmlBody()) ? "html" : "text");
        String composeMode = Strings.isNotBlank(req.getComposeMode()) ? req.getComposeMode() : "new";
        String sendStatus = "draft";
        Integer enableStatus = req.getEnableStatus() != null ? req.getEnableStatus() : 1;
        Date now = new Date();
        if (Strings.isBlank(req.getId())) {
            return MetaFactory.insert(UserEmailDraft.class)
                    .value("tenantCode", tenantCode)
                    .value("userId", userId)
                    .value("emailAccountId", req.getEmailAccountId())
                    .value("fromName", trimToNull(req.getFromName()))
                    .value("subject", trimToNull(req.getSubject()))
                    .value("toJson", toJson(req.getTo()))
                    .value("ccJson", toJson(req.getCc()))
                    .value("bccJson", toJson(req.getBcc()))
                    .value("bodyType", bodyType)
                    .value("textBody", req.getTextBody())
                    .value("htmlBody", req.getHtmlBody())
                    .value("attachmentIdsJson", toJson(req.getAttachmentIds()))
                    .value("mailAttachmentRefsJson", toJson(req.getMailAttachmentRefs()))
                    .value("sourceMailId", trimToNull(req.getSourceMailId()))
                    .value("composeMode", composeMode)
                    .value("inReplyToMessageId", trimToNull(req.getInReplyToMessageId()))
                    .value("referencesHeader", trimToNull(req.getReferencesHeader()))
                    .value("autoSaveAt", now)
                    .value("sendStatus", sendStatus)
                    .value("enableStatus", enableStatus)
                    .save();
        }

        Map<String, Object> exists = MetaFactory.query(UserEmailDraft.class)
                .where(
                        Filter.eq("id", req.getId()),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .one();
        if (exists == null || exists.isEmpty()) {
            throw new IllegalArgumentException("草稿不存在或无权限");
        }
        MetaFactory.update(UserEmailDraft.class)
                .where(
                        Filter.eq("id", req.getId()),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("emailAccountId", req.getEmailAccountId())
                .value("fromName", trimToNull(req.getFromName()))
                .value("subject", trimToNull(req.getSubject()))
                .value("toJson", toJson(req.getTo()))
                .value("ccJson", toJson(req.getCc()))
                .value("bccJson", toJson(req.getBcc()))
                .value("bodyType", bodyType)
                .value("textBody", req.getTextBody())
                .value("htmlBody", req.getHtmlBody())
                .value("attachmentIdsJson", toJson(req.getAttachmentIds()))
                .value("mailAttachmentRefsJson", toJson(req.getMailAttachmentRefs()))
                .value("sourceMailId", trimToNull(req.getSourceMailId()))
                .value("composeMode", composeMode)
                .value("inReplyToMessageId", trimToNull(req.getInReplyToMessageId()))
                .value("referencesHeader", trimToNull(req.getReferencesHeader()))
                .value("autoSaveAt", now)
                .value("enableStatus", enableStatus)
                .save();
        return req.getId();
    }

    public boolean remove(String userId, String id) {
        MetaFactory.update(UserEmailDraft.class)
                .where(
                        Filter.eq("id", id),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("delStatus", 1)
                .value("deleteAt", new Date())
                .save();
        return true;
    }

    public void markSent(String userId, String id) {
        if (Strings.isBlank(id)) {
            return;
        }
        MetaFactory.update(UserEmailDraft.class)
                .where(
                        Filter.eq("id", id),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("sendStatus", "sent")
                .value("autoSaveAt", new Date())
                .save();
    }

    public SendEmailRequest toSendRequest(String userId, String id) {
        EmailDraftDto draft = get(userId, id);
        if (draft == null) {
            throw new IllegalArgumentException("草稿不存在");
        }
        SendEmailRequest req = new SendEmailRequest();
        req.setDraftId(draft.getId());
        req.setEmailAccountId(draft.getEmailAccountId());
        req.setFromName(draft.getFromName());
        req.setTo(draft.getTo());
        req.setCc(draft.getCc());
        req.setBcc(draft.getBcc());
        req.setSubject(draft.getSubject());
        req.setTextBody(draft.getTextBody());
        req.setHtmlBody(draft.getHtmlBody());
        req.setAttachmentIds(draft.getAttachmentIds());
        req.setMailAttachmentRefs(draft.getMailAttachmentRefs());
        req.setComposeMode(draft.getComposeMode());
        req.setSourceMailId(draft.getSourceMailId());
        req.setInReplyToMessageId(draft.getInReplyToMessageId());
        req.setReferencesHeader(draft.getReferencesHeader());
        return req;
    }

    public String saveFromSendRequest(String userId, String tenantCode, SendEmailRequest request) {
        EmailDraftUpsertRequest draft = new EmailDraftUpsertRequest();
        draft.setId(request.getDraftId());
        draft.setEmailAccountId(request.getEmailAccountId());
        draft.setFromName(request.getFromName());
        draft.setSubject(request.getSubject());
        draft.setTo(request.getTo());
        draft.setCc(request.getCc());
        draft.setBcc(request.getBcc());
        draft.setBodyType(Strings.isNotBlank(request.getHtmlBody()) ? "html" : "text");
        draft.setTextBody(request.getTextBody());
        draft.setHtmlBody(request.getHtmlBody());
        draft.setAttachmentIds(request.getAttachmentIds());
        draft.setMailAttachmentRefs(request.getMailAttachmentRefs());
        draft.setSourceMailId(request.getSourceMailId());
        draft.setComposeMode(request.getComposeMode());
        draft.setInReplyToMessageId(request.getInReplyToMessageId());
        draft.setReferencesHeader(request.getReferencesHeader());
        draft.setEnableStatus(1);
        return createOrUpdate(userId, tenantCode, draft);
    }

    private Filter[] buildFilters(String userId, String emailAccountId) {
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        if (Strings.isNotBlank(emailAccountId)) {
            filters.add(Filter.eq("emailAccountId", emailAccountId));
        }
        return filters.toArray(new Filter[0]);
    }

    private EmailDraftDto toDto(Map<String, Object> row) {
        EmailDraftDto dto = new EmailDraftDto();
        dto.setId(Objects.toString(row.get("id"), null));
        dto.setEmailAccountId(Objects.toString(row.get("emailAccountId"), null));
        dto.setFromName(Objects.toString(row.get("fromName"), null));
        dto.setSubject(Objects.toString(row.get("subject"), null));
        dto.setTo(parseAddressList(row.get("toJson")));
        dto.setCc(parseAddressList(row.get("ccJson")));
        dto.setBcc(parseAddressList(row.get("bccJson")));
        dto.setBodyType(Objects.toString(row.get("bodyType"), null));
        dto.setTextBody(Objects.toString(row.get("textBody"), null));
        dto.setHtmlBody(Objects.toString(row.get("htmlBody"), null));
        dto.setAttachmentIds(parseStringList(row.get("attachmentIdsJson")));
        dto.setMailAttachmentRefs(parseMailRefs(row.get("mailAttachmentRefsJson")));
        dto.setSourceMailId(Objects.toString(row.get("sourceMailId"), null));
        dto.setComposeMode(Objects.toString(row.get("composeMode"), null));
        dto.setInReplyToMessageId(Objects.toString(row.get("inReplyToMessageId"), null));
        dto.setReferencesHeader(Objects.toString(row.get("referencesHeader"), null));
        dto.setAutoSaveAt(row.get("autoSaveAt") instanceof Date d ? d : null);
        dto.setSendStatus(Objects.toString(row.get("sendStatus"), null));
        dto.setEnableStatus(intVal(row.get("enableStatus")));
        dto.setCreateAt(row.get("createAt") instanceof Date d ? d : null);
        dto.setUpdateAt(row.get("updateAt") instanceof Date d ? d : null);
        return dto;
    }

    private boolean matchesKeyword(EmailDraftDto dto, String keyword) {
        if (Strings.isBlank(keyword)) {
            return true;
        }
        String lower = keyword.trim().toLowerCase();
        if (dto.getSubject() != null && dto.getSubject().toLowerCase().contains(lower)) {
            return true;
        }
        return dto.getTo() != null && dto.getTo().stream().anyMatch(it ->
                it != null && ((it.getName() != null && it.getName().toLowerCase().contains(lower))
                        || (it.getAddress() != null && it.getAddress().toLowerCase().contains(lower))));
    }

    private List<EmailAddressDto> parseAddressList(Object raw) {
        String json = Objects.toString(raw, null);
        if (Strings.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseObject(json, new TypeReference<List<EmailAddressDto>>() {
        });
    }

    private List<String> parseStringList(Object raw) {
        String json = Objects.toString(raw, null);
        if (Strings.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseObject(json, new TypeReference<List<String>>() {
        });
    }

    private List<MailAttachmentRefDto> parseMailRefs(Object raw) {
        String json = Objects.toString(raw, null);
        if (Strings.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseObject(json, new TypeReference<List<MailAttachmentRefDto>>() {
        });
    }

    private String toJson(Object value) {
        return value == null ? null : JSON.toJSONString(value);
    }

    private Integer intVal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
