package cn.geelato.web.platform.srv.email.service;

import cn.geelato.core.util.EncryptUtils;
import cn.geelato.meta.Attachment;
import cn.geelato.meta.UserEmailAccount;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.email.dto.EmailAddressDto;
import cn.geelato.web.platform.srv.email.dto.EmailComposeContextDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageDetailDto;
import cn.geelato.web.platform.srv.email.dto.MailAttachmentRefDto;
import cn.geelato.web.platform.srv.email.dto.SendEmailRequest;
import cn.geelato.web.platform.srv.email.dto.SendEmailResult;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

@Service
@Slf4j
public class EmailComposeService {

    @Autowired
    private EmailInboxService emailInboxService;

    @Autowired
    private EmailQueryService emailQueryService;

    @Autowired
    private EmailContactService emailContactService;

    @Autowired
    private EmailDraftService emailDraftService;

    @Autowired
    private FileHandler fileHandler;

    public SendEmailResult send(String userId, String tenantCode, SendEmailRequest req) throws Exception {
        if (req == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if ((req.getTo() == null || req.getTo().isEmpty())
                && (req.getCc() == null || req.getCc().isEmpty())
                && (req.getBcc() == null || req.getBcc().isEmpty())) {
            throw new IllegalArgumentException("至少需要一个收件人");
        }

        EmailAccountConfig account = getAccountConfig(userId, req.getEmailAccountId());
        JavaMailSenderImpl sender = buildMailSender(account);
        MimeMessage mimeMessage = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
        String fromName = Strings.isNotBlank(req.getFromName()) ? req.getFromName() : account.fromName();
        if (Strings.isNotBlank(fromName)) {
            helper.setFrom(account.emailAddress(), fromName);
        } else {
            helper.setFrom(account.emailAddress());
        }
        applyRecipients(helper, req.getTo(), RecipientType.TO);
        applyRecipients(helper, req.getCc(), RecipientType.CC);
        applyRecipients(helper, req.getBcc(), RecipientType.BCC);
        helper.setSubject(Strings.isNotBlank(req.getSubject()) ? req.getSubject() : "(无主题)");
        applyBody(helper, req.getTextBody(), req.getHtmlBody());
        applyThreadHeaders(mimeMessage, req);
        applyPlatformAttachments(helper, req.getAttachmentIds());
        applyMailAttachments(userId, helper, req.getMailAttachmentRefs());

        try {
            sender.send(mimeMessage);
        } catch (Exception ex) {
            if (Boolean.TRUE.equals(req.getSaveAsDraftOnFail())) {
                emailDraftService.saveFromSendRequest(userId, tenantCode, req);
            }
            throw ex;
        }

        if (Strings.isNotBlank(req.getDraftId())) {
            emailDraftService.markSent(userId, req.getDraftId());
        }
        emailContactService.touchSentContacts(userId, tenantCode, account.id(), mergeRecipients(req.getTo(), req.getCc(), req.getBcc()));

        SendEmailResult result = new SendEmailResult();
        result.setDraftId(req.getDraftId());
        result.setMessageId(mimeMessage.getMessageID());
        result.setSentAt(new Date());
        result.setContactSynced(true);
        return result;
    }

    public EmailComposeContextDto buildComposeContext(String userId, String mailId, String mode) throws Exception {
        String composeMode = Strings.isNotBlank(mode) ? mode : "reply";
        EmailMessageDetailDto detail = emailQueryService.getMessageDetail(userId, mailId);
        EmailComposeContextDto dto = new EmailComposeContextDto();
        dto.setSourceMailId(mailId);
        dto.setComposeMode(composeMode);
        dto.setSubject(buildReplySubject(detail.getSubject(), composeMode));
        dto.setInReplyToMessageId(detail.getMessageId());
        dto.setReferencesHeader(detail.getMessageId());
        dto.setTo(resolveReplyTo(detail, composeMode));
        dto.setCc(resolveReplyCc(userId, detail, composeMode));
        dto.setQuotedText(buildQuotedText(detail));
        dto.setQuotedHtml(buildQuotedHtml(detail));
        return dto;
    }

    private EmailAccountConfig getAccountConfig(String userId, String emailAccountId) {
        Map<String, Object> row;
        if (Strings.isNotBlank(emailAccountId)) {
            row = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("id", emailAccountId),
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0),
                            Filter.eq("enableStatus", 1)
                    )
                    .one();
        } else {
            row = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0),
                            Filter.eq("enableStatus", 1)
                    )
                    .order(Order.desc("defaultFlag"), Order.desc("createAt"))
                    .one();
        }
        if (row == null || row.isEmpty()) {
            throw new IllegalArgumentException("email account not found");
        }
        String authType = Objects.toString(row.get("authType"), "auth_code");
        if ("oauth2".equalsIgnoreCase(authType)) {
            throw new IllegalArgumentException("oauth2 send not implemented");
        }
        String smtpSecretEnc = Objects.toString(row.get("smtpAuthSecret"), null);
        String smtpSecret = Strings.isNotBlank(smtpSecretEnc) ? EncryptUtils.decrypt(smtpSecretEnc) : null;
        String smtpUser = Objects.toString(row.get("smtpAuthUser"), null);
        String emailAddress = Objects.toString(row.get("emailAddress"), null);
        if (Strings.isBlank(smtpUser)) {
            smtpUser = emailAddress;
        }
        String smtpHost = Objects.toString(row.get("smtpHost"), null);
        Integer smtpPort = intVal(row.get("smtpPort"));
        int smtpSsl = intVal0(row.get("smtpSsl"));
        int smtpStarttls = intVal0(row.get("smtpStarttls"));
        if (Strings.isBlank(smtpHost) || smtpPort == null || smtpPort <= 0 || Strings.isBlank(smtpUser) || Strings.isBlank(smtpSecret)) {
            throw new IllegalArgumentException("email smtp config invalid");
        }
        return new EmailAccountConfig(
                Objects.toString(row.get("id"), null),
                emailAddress,
                smtpHost,
                smtpPort,
                smtpSsl == 1,
                smtpStarttls == 1,
                smtpUser,
                smtpSecret,
                Objects.toString(row.get("smtpFromName"), null)
        );
    }

    private JavaMailSenderImpl buildMailSender(EmailAccountConfig account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.smtpHost());
        sender.setPort(account.smtpPort());
        sender.setUsername(account.smtpUser());
        sender.setPassword(account.smtpSecret());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setProtocol("smtp");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if (account.smtpSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", account.smtpHost());
        }
        if (account.smtpStarttls()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    private void applyRecipients(MimeMessageHelper helper, List<EmailAddressDto> addresses, RecipientType type) throws Exception {
        String[] values = toAddressArray(addresses);
        if (values.length == 0) {
            return;
        }
        switch (type) {
            case TO -> helper.setTo(values);
            case CC -> helper.setCc(values);
            case BCC -> helper.setBcc(values);
        }
    }

    private void applyBody(MimeMessageHelper helper, String textBody, String htmlBody) throws Exception {
        if (Strings.isNotBlank(textBody) && Strings.isNotBlank(htmlBody)) {
            helper.setText(textBody, htmlBody);
        } else if (Strings.isNotBlank(htmlBody)) {
            helper.setText(htmlBody, true);
        } else {
            helper.setText(Strings.isNotBlank(textBody) ? textBody : "");
        }
    }

    private void applyThreadHeaders(MimeMessage mimeMessage, SendEmailRequest req) throws Exception {
        if (Strings.isNotBlank(req.getInReplyToMessageId())) {
            mimeMessage.setHeader("In-Reply-To", req.getInReplyToMessageId());
        }
        if (Strings.isNotBlank(req.getReferencesHeader())) {
            mimeMessage.setHeader("References", req.getReferencesHeader());
        }
    }

    private void applyPlatformAttachments(MimeMessageHelper helper, List<String> attachmentIds) throws Exception {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        for (String attachmentId : attachmentIds) {
            if (Strings.isBlank(attachmentId)) {
                continue;
            }
            Attachment attachment = fileHandler.getAttachment(attachmentId);
            if (attachment == null) {
                throw new IllegalArgumentException("attachment not found: " + attachmentId);
            }
            try (InputStream inputStream = fileHandler.toInputStream(attachment)) {
                if (inputStream == null) {
                    throw new IllegalArgumentException("attachment stream not found: " + attachmentId);
                }
                helper.addAttachment(
                        Strings.isNotBlank(attachment.getName()) ? attachment.getName() : attachmentId,
                        new ByteArrayResource(inputStream.readAllBytes()),
                        Strings.isNotBlank(attachment.getType()) ? attachment.getType() : "application/octet-stream"
                );
            }
        }
    }

    private void applyMailAttachments(String userId, MimeMessageHelper helper, List<MailAttachmentRefDto> refs) throws Exception {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        for (MailAttachmentRefDto ref : refs) {
            if (ref == null || Strings.isBlank(ref.getMailId()) || Strings.isBlank(ref.getPartId())) {
                continue;
            }
            EmailInboxService.DownloadAttachment attachment = emailInboxService.openAttachmentStream(userId, ref.getMailId(), ref.getPartId());
            try (InputStream inputStream = attachment.inputStream()) {
                helper.addAttachment(
                        Strings.isNotBlank(attachment.fileName()) ? attachment.fileName() : ("attachment-" + ref.getPartId()),
                        new ByteArrayResource(inputStream.readAllBytes()),
                        Strings.isNotBlank(attachment.contentType()) ? attachment.contentType() : "application/octet-stream"
                );
            }
        }
    }

    private List<EmailAddressDto> mergeRecipients(List<EmailAddressDto> to, List<EmailAddressDto> cc, List<EmailAddressDto> bcc) {
        List<EmailAddressDto> list = new ArrayList<>();
        if (to != null) {
            list.addAll(to);
        }
        if (cc != null) {
            list.addAll(cc);
        }
        if (bcc != null) {
            list.addAll(bcc);
        }
        return list;
    }

    private List<EmailAddressDto> resolveReplyTo(EmailMessageDetailDto detail, String mode) {
        if ("forward".equalsIgnoreCase(mode)) {
            return Collections.emptyList();
        }
        if (detail.getFrom() == null) {
            return Collections.emptyList();
        }
        return List.of(detail.getFrom());
    }

    private List<EmailAddressDto> resolveReplyCc(String userId, EmailMessageDetailDto detail, String mode) {
        if (!"reply_all".equalsIgnoreCase(mode)) {
            return Collections.emptyList();
        }
        Set<String> ownEmails = listOwnEmails(userId);
        LinkedHashMap<String, EmailAddressDto> map = new LinkedHashMap<>();
        if (detail.getTo() != null) {
            for (EmailAddressDto address : detail.getTo()) {
                putIfExternal(map, ownEmails, address);
            }
        }
        if (detail.getCc() != null) {
            for (EmailAddressDto address : detail.getCc()) {
                putIfExternal(map, ownEmails, address);
            }
        }
        if (detail.getFrom() != null) {
            String key = normalize(detail.getFrom().getAddress());
            map.remove(key);
        }
        return new ArrayList<>(map.values());
    }

    private Set<String> listOwnEmails(String userId) {
        Set<String> set = new LinkedHashSet<>();
        List<Map<String, Object>> rows = MetaFactory.query(UserEmailAccount.class)
                .where(Filter.eq("userId", userId), Filter.eq("delStatus", 0))
                .list();
        for (Map<String, Object> row : rows) {
            String email = normalize(Objects.toString(row.get("emailAddress"), null));
            if (Strings.isNotBlank(email)) {
                set.add(email);
            }
        }
        return set;
    }

    private void putIfExternal(Map<String, EmailAddressDto> map, Set<String> ownEmails, EmailAddressDto address) {
        if (address == null || Strings.isBlank(address.getAddress())) {
            return;
        }
        String email = normalize(address.getAddress());
        if (Strings.isBlank(email) || ownEmails.contains(email)) {
            return;
        }
        map.putIfAbsent(email, address);
    }

    private String buildReplySubject(String subject, String mode) {
        String raw = Strings.isNotBlank(subject) ? subject : "(无主题)";
        if ("forward".equalsIgnoreCase(mode)) {
            return raw.regionMatches(true, 0, "Fwd:", 0, 4) ? raw : "Fwd: " + raw;
        }
        return raw.regionMatches(true, 0, "Re:", 0, 3) ? raw : "Re: " + raw;
    }

    private String buildQuotedText(EmailMessageDetailDto detail) {
        String body = Strings.isNotBlank(detail.getTextBody()) ? detail.getTextBody() : "";
        String from = detail.getFrom() != null ? formatAddress(detail.getFrom()) : "";
        return "\n\n---- Original Message ----\n"
                + "From: " + from + "\n"
                + "Subject: " + Objects.toString(detail.getSubject(), "") + "\n\n"
                + body;
    }

    private String buildQuotedHtml(EmailMessageDetailDto detail) {
        String body = Strings.isNotBlank(detail.getHtmlBody()) ? detail.getHtmlBody()
                : escapeHtml(Strings.isNotBlank(detail.getTextBody()) ? detail.getTextBody() : "").replace("\n", "<br/>");
        String from = detail.getFrom() != null ? escapeHtml(formatAddress(detail.getFrom())) : "";
        String subject = escapeHtml(Objects.toString(detail.getSubject(), ""));
        return "<br/><br/><div>---- Original Message ----</div>"
                + "<div>From: " + from + "</div>"
                + "<div>Subject: " + subject + "</div><br/>"
                + "<blockquote style=\"margin:0 0 0 8px;padding-left:12px;border-left:2px solid #d9d9d9;\">"
                + body
                + "</blockquote>";
    }

    private String[] toAddressArray(List<EmailAddressDto> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return new String[0];
        }
        List<String> list = new ArrayList<>();
        for (EmailAddressDto address : addresses) {
            if (address == null || Strings.isBlank(address.getAddress())) {
                continue;
            }
            String email = address.getAddress().trim();
            if (Strings.isNotBlank(address.getName())) {
                try {
                    list.add(new InternetAddress().toUnicodeString());
                } catch (Exception ignored) {
                    list.add(email);
                }
            } else {
                list.add(email);
            }
        }
        return list.toArray(new String[0]);
    }

    private String formatAddress(EmailAddressDto address) {
        if (address == null) {
            return "";
        }
        if (Strings.isNotBlank(address.getName())) {
            return address.getName() + " <" + address.getAddress() + ">";
        }
        return Objects.toString(address.getAddress(), "");
    }

    private String normalize(String raw) {
        return Strings.isBlank(raw) ? null : raw.trim().toLowerCase();
    }

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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

    private int intVal0(Object raw) {
        Integer value = intVal(raw);
        return value != null ? value : 0;
    }

    private enum RecipientType {
        TO, CC, BCC
    }

    private record EmailAccountConfig(
            String id,
            String emailAddress,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            boolean smtpStarttls,
            String smtpUser,
            String smtpSecret,
            String fromName
    ) {
    }
}
