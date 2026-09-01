package cn.geelato.mail.service;

import cn.geelato.mail.entity.MailAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 邮件协议服务（jakarta.mail）：IMAP 收信同步、SMTP 发送、账户连通性验证。
 *
 * <p>加密模式映射：
 * <ul>
 *   <li>ssl  → imaps/smtps（SSL 直连，典型端口 993/465）</li>
 *   <li>tls  → imap/smtp + STARTTLS（典型端口 143/587）</li>
 *   <li>none → imap/smtp 明文（典型端口 143/25）</li>
 * </ul>
 *
 * <p>P0 同步范围：仅 INBOX（收件箱），单次最多拉取 {@link #SYNC_BATCH_LIMIT} 封；
 * 附件只记录元数据（名称/大小/类型），内容下载在 P1 实现。
 */
@Slf4j
@Service
public class MailProtocolService {

    /** 单次同步最大拉取封数（防止首次同步打满内存） */
    private static final int SYNC_BATCH_LIMIT = 200;
    /** 连接/读写超时（毫秒） */
    private static final int TIMEOUT_MS = 15000;
    /** 单封邮件回源大小上限（源码查看/附件提取共用，防大邮件打满内存） */
    static final int MAX_FETCH_BYTES = 50 * 1024 * 1024;
    /** 附件元数据序列化（与 MailMessageService 读侧解析同款 Jackson，消除手拼 JSON 转义不全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 验证账户连通性（IMAP 登录 + SMTP 连接依次测试）。
     *
     * @return 错误信息；null 表示成功
     */
    public String verify(MailAccount account, String plainPassword) {
        try (Store store = openStore(account, plainPassword)) {
            store.getFolder("INBOX").exists();
        } catch (Exception e) {
            return "收信服务器连接失败: " + rootMessage(e);
        }
        try {
            Transport transport = smtpSession(account).getTransport(smtpProtocol(account));
            try {
                transport.connect(account.getOutgoingHost(), account.getOutgoingPort(),
                        account.getUsername(), plainPassword);
            } finally {
                transport.close();
            }
        } catch (Exception e) {
            return "发信服务器连接失败: " + rootMessage(e);
        }
        return null;
    }

    /**
     * 从 IMAP INBOX 拉取邮件（最多 {@link #SYNC_BATCH_LIMIT} 封，最新的优先）。
     * 调用方负责去重落库。
     */
    public List<ParsedMail> fetchInbox(MailAccount account, String plainPassword) throws MessagingException {
        List<ParsedMail> result = new ArrayList<>();
        try (Store store = openStore(account, plainPassword)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                int total = inbox.getMessageCount();
                if (total == 0) {
                    return result;
                }
                int from = Math.max(1, total - SYNC_BATCH_LIMIT + 1);
                Message[] messages = inbox.getMessages(from, total);
                // 批量预取 envelope + flags，避免逐封回连
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(FetchProfile.Item.FLAGS);
                fp.add(UIDFolder.FetchProfileItem.UID);
                inbox.fetch(messages, fp);
                UIDFolder uidFolder = (UIDFolder) inbox;
                for (Message msg : messages) {
                    try {
                        result.add(parse(msg, uidFolder.getUID(msg)));
                    } catch (Exception e) {
                        // 单封解析失败不阻断整批同步，记日志跳过
                        log.warn("解析邮件失败（subject={}）: {}", safeSubject(msg), e.getMessage());
                    }
                }
            } finally {
                inbox.close(false);
            }
        }
        return result;
    }

    /**
     * SMTP 发送邮件（HTML 正文）。
     *
     * @return 发送后服务端分配的 Message-ID（可能为空）
     */
    public String send(MailAccount account, String plainPassword, ComposeMail compose)
            throws MessagingException {
        return send(account, plainPassword, compose, List.of());
    }

    /**
     * SMTP 发送邮件（HTML 正文 + 真实附件，multipart/mixed）。
     *
     * @param attachments 已解析的本地附件（token 已校验归属）；空列表时退化为纯 HTML 发送
     * @return 发送后服务端分配的 Message-ID（可能为空）
     */
    public String send(MailAccount account, String plainPassword, ComposeMail compose,
                       List<ResolvedMailFile> attachments) throws MessagingException {
        Session session = smtpSession(account);
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(inetAddress(account.getEmail(), account.getName()));
        msg.setRecipients(Message.RecipientType.TO, toAddresses(compose.to()));
        if (compose.cc() != null && !compose.cc().isEmpty()) {
            msg.setRecipients(Message.RecipientType.CC, toAddresses(compose.cc()));
        }
        if (compose.bcc() != null && !compose.bcc().isEmpty()) {
            msg.setRecipients(Message.RecipientType.BCC, toAddresses(compose.bcc()));
        }
        msg.setSubject(compose.subject() == null ? "" : compose.subject(), "UTF-8");
        msg.setSentDate(new Date());
        // RFC 5322 §3.6.4：回复携带 In-Reply-To/References（父邮件 Message-ID），
        // 供收件方（含外部客户端）做线程归组；新邮件/无引用时省略两头的生成
        if (compose.inReplyTo() != null && !compose.inReplyTo().isBlank()) {
            String parent = compose.inReplyTo().trim();
            msg.setHeader("In-Reply-To", parent);
            msg.setHeader("References", parent);
        }
        String html = compose.htmlContent() == null ? "" : compose.htmlContent();
        if (attachments == null || attachments.isEmpty()) {
            msg.setContent(html, "text/html; charset=UTF-8");
        } else {
            MimeMultipart mixed = new MimeMultipart("mixed");
            MimeBodyPart body = new MimeBodyPart();
            body.setContent(html, "text/html; charset=UTF-8");
            mixed.addBodyPart(body);
            for (ResolvedMailFile file : attachments) {
                MimeBodyPart part = new MimeBodyPart();
                try {
                    // ST37-B1：显式 base64（替代 encoding=null 自动选择）。JavaMail 对 text/*
                    // 附件自动选择 quoted-printable 时会做 LF→CRLF 规范化，破坏字节保真
                    // （校验和/diff/清单类附件失效）；base64 为二进制安全编码，字节级无损。
                    part.attachFile(file.path().toFile(), file.contentType(), "base64");
                } catch (java.io.IOException e) {
                    throw new MessagingException("读取附件失败: " + file.name(), e);
                }
                part.setDisposition(Part.ATTACHMENT);
                // RFC 2047 encoded-word 编码非 ASCII 文件名（QQ/163 等国内服务商兼容性好）
                // UTF-8 为 JVM 必备字符集，UnsupportedEncodingException 实际不可达，包装为 MessagingException
                try {
                    part.setFileName(MimeUtility.encodeText(file.name(), StandardCharsets.UTF_8.name(), "B"));
                } catch (UnsupportedEncodingException e) {
                    throw new MessagingException("UTF-8 charset unavailable", e);
                }
                mixed.addBodyPart(part);
            }
            msg.setContent(mixed);
        }

        Transport transport = session.getTransport(smtpProtocol(account));
        try {
            transport.connect(account.getOutgoingHost(), account.getOutgoingPort(),
                    account.getUsername(), plainPassword);
            transport.sendMessage(msg, msg.getAllRecipients());
        } finally {
            transport.close();
        }
        return msg.getMessageID();
    }

    // ==================== P1：源码查看 / 附件内容回源 ====================

    /**
     * 按 IMAP UID 回源抓取完整 RFC822 原始报文（源码查看 / 附件解析共用）。
     *
     * <p>实现说明：jakarta.mail IMAP provider 的 part 级 FETCH（BODY[part]）需在整个
     * 流式消费期间保持 folder 连接打开，生命周期管理复杂；这里采用整封拉取 + 本地解析
     * （内容等价），并以 {@link #MAX_FETCH_BYTES} 兜底内存上限。
     *
     * @return 原始报文字节；UID 在服务器上不存在（已删除/移动）返回 null
     * @throws MessagingException 连接/认证失败、邮件超过大小上限等
     */
    public byte[] fetchRawMessage(MailAccount account, String plainPassword, String imapUid)
            throws MessagingException {
        long uid;
        try {
            uid = Long.parseLong(imapUid.trim());
        } catch (NumberFormatException e) {
            throw new MessagingException("非法的 IMAP UID: " + imapUid);
        }
        try (Store store = openStore(account, plainPassword)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            try {
                Message msg = ((UIDFolder) inbox).getMessageByUID(uid);
                if (msg == null) {
                    return null;
                }
                int size = msg.getSize();
                if (size > MAX_FETCH_BYTES) {
                    throw new MessagingException("邮件大小 " + size + " 字节超过 "
                            + (MAX_FETCH_BYTES / 1024 / 1024) + "MB 回源上限");
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(size, 8192));
                try {
                    msg.writeTo(baos);
                } catch (java.io.IOException e) {
                    throw new MessagingException("读取邮件原始报文失败", e);
                }
                if (baos.size() > MAX_FETCH_BYTES) {
                    throw new MessagingException("邮件大小超过 " + (MAX_FETCH_BYTES / 1024 / 1024) + "MB 回源上限");
                }
                return baos.toByteArray();
            } finally {
                inbox.close(false);
            }
        }
    }

    /**
     * 按附件下标回源提取附件内容（与同步落库的附件顺序同口径，见 {@link MailMimeSupport}）。
     *
     * @return 附件内容；下标越界返回 null（调用方转 40400）
     */
    public FetchedAttachment fetchAttachment(MailAccount account, String plainPassword,
                                             String imapUid, int attachmentIndex) throws MessagingException {
        byte[] raw = fetchRawMessage(account, plainPassword, imapUid);
        if (raw == null) {
            return null;
        }
        try {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()),
                    new java.io.ByteArrayInputStream(raw));
            List<Part> attachments = MailMimeSupport.collectAttachments(msg);
            if (attachmentIndex < 0 || attachmentIndex >= attachments.size()) {
                return null;
            }
            Part part = attachments.get(attachmentIndex);
            byte[] content;
            // ST37-B1：getInputStream() 对 text/* part 走 content-handler，会做换行规范化
            // （LF→CRLF）破坏字节保真；改用原始编码流 + 显式 CTE 解码，任意附件字节级还原
            try (InputStream in = decodeRawStream(part)) {
                content = in.readAllBytes();
            }
            String contentType = part.getContentType();
            if (contentType != null) {
                int semicolon = contentType.indexOf(';');
                contentType = (semicolon > 0 ? contentType.substring(0, semicolon) : contentType)
                        .trim().toLowerCase();
            }
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            String fileName = MailMimeSupport.decodeFileName(part.getFileName());
            return new FetchedAttachment(content, contentType,
                    fileName == null || fileName.isBlank() ? "attachment" : fileName);
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("解析邮件 MIME 结构失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 附件 Part 的字节保真读取流：原始编码流（getRawInputStream，未经 content-handler）
     * + 按 Content-Transfer-Encoding 显式解码。
     *
     * <p>与 {@link Part#getInputStream()} 的差异：后者对 text/* part 走 content-handler
     * 读取时会做换行规范化（LF→CRLF），导致附件字节与发件原件不一致（ST37-B1）。
     * 7bit/8bit/binary/无 CTE 为恒等变换，直接透传原始流。
     */
    private InputStream decodeRawStream(Part part) throws MessagingException, java.io.IOException {
        if (!(part instanceof MimeBodyPart mbp)) {
            // collectAttachments 产出的附件恒为 MimeBodyPart；此分支理论不可达，维持既有行为
            return part.getInputStream();
        }
        InputStream raw = mbp.getRawInputStream();
        String encoding = mbp.getEncoding();
        if (encoding == null || encoding.isBlank()) {
            return raw;
        }
        String norm = encoding.trim().toLowerCase();
        if ("7bit".equals(norm) || "8bit".equals(norm) || "binary".equals(norm)) {
            return raw;
        }
        return MimeUtility.decode(raw, encoding);
    }

    private Store openStore(MailAccount account, String plainPassword) throws MessagingException {
        Properties props = new Properties();
        String protocol;
        if ("pop3".equalsIgnoreCase(account.getIncomingProtocol())) {
            protocol = "ssl".equalsIgnoreCase(account.getIncomingEncryption()) ? "pop3s" : "pop3";
        } else {
            protocol = "ssl".equalsIgnoreCase(account.getIncomingEncryption()) ? "imaps" : "imap";
        }
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", account.getIncomingHost());
        props.put("mail." + protocol + ".port", String.valueOf(account.getIncomingPort()));
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put("mail." + protocol + ".timeout", String.valueOf(TIMEOUT_MS));
        if ("tls".equalsIgnoreCase(account.getIncomingEncryption())) {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".starttls.required", "true");
        }
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(account.getIncomingHost(), account.getIncomingPort(),
                account.getUsername(), plainPassword);
        return store;
    }

    private Session smtpSession(MailAccount account) {
        String protocol = smtpProtocol(account);
        Properties props = new Properties();
        props.put("mail.transport.protocol", protocol);
        props.put("mail." + protocol + ".host", account.getOutgoingHost());
        props.put("mail." + protocol + ".port", String.valueOf(account.getOutgoingPort()));
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put("mail." + protocol + ".timeout", String.valueOf(TIMEOUT_MS));
        props.put("mail." + protocol + ".auth", "true");
        if ("ssl".equalsIgnoreCase(account.getOutgoingEncryption())) {
            props.put("mail." + protocol + ".ssl.enable", "true");
        } else if ("tls".equalsIgnoreCase(account.getOutgoingEncryption())) {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".starttls.required", "true");
        }
        return Session.getInstance(props);
    }

    private String smtpProtocol(MailAccount account) {
        return "ssl".equalsIgnoreCase(account.getOutgoingEncryption()) ? "smtps" : "smtp";
    }

    private ParsedMail parse(Message msg, long uid) throws Exception {
        ParsedMail parsed = new ParsedMail();
        parsed.setImapUid(String.valueOf(uid));
        parsed.setMessageId(msg instanceof MimeMessage mm ? mm.getMessageID() : null);
        // V82: In-Reply-To 优先，缺失时回退 References 链末位（会话视图归组依据）
        parsed.setInReplyTo(resolveInReplyTo(msg));
        parsed.setSubject(decodeText(msg.getSubject()));
        InternetAddress[] from = toInternetAddresses(msg.getFrom());
        if (from.length > 0) {
            parsed.setFromEmail(from[0].getAddress());
            parsed.setFromName(decodeText(from[0].getPersonal()));
        }
        parsed.setToJson(addressesToJson(msg.getRecipients(Message.RecipientType.TO)));
        parsed.setCcJson(addressesToJson(msg.getRecipients(Message.RecipientType.CC)));
        Date date = msg.getSentDate() != null ? msg.getSentDate() : msg.getReceivedDate();
        parsed.setSendDate(date != null ? date : new Date());
        parsed.setMailSize(msg.getSize() > 0 ? msg.getSize() : 0);
        parsed.setReadStatus(msg.isSet(jakarta.mail.Flags.Flag.SEEN) ? "read" : "unread");
        parsed.setStarred(msg.isSet(jakarta.mail.Flags.Flag.FLAGGED));
        // 正文 + 附件（递归遍历 MIME 树）
        ContentExtractor extractor = new ContentExtractor();
        extractor.walk(msg);
        parsed.setContentHtml(extractor.html);
        parsed.setContentText(extractor.text);
        parsed.setHasAttachment(extractor.hasAttachment ? 1 : 0);
        parsed.setAttachmentsJson(extractor.attachmentsJson());
        String body = parsed.getContentText() != null ? parsed.getContentText() : stripHtml(parsed.getContentHtml());
        parsed.setPreview(body == null ? "" : body.substring(0, Math.min(200, body.length())));
        return parsed;
    }

    /**
     * 解析父邮件引用（V82）：In-Reply-To 头优先；缺失时取 References 链末位 Message-ID。
     * 返回值做长度截断（512，与 mail_message.in_reply_to 列宽对齐）。
     */
    private String resolveInReplyTo(Message msg) throws Exception {
        String[] inReplyTo = msg.getHeader("In-Reply-To");
        if (inReplyTo != null && inReplyTo.length > 0 && inReplyTo[0] != null && !inReplyTo[0].isBlank()) {
            String value = inReplyTo[0].trim();
            return value.substring(0, Math.min(512, value.length()));
        }
        String[] references = msg.getHeader("References");
        if (references == null || references.length == 0 || references[0] == null) {
            return null;
        }
        String joined = String.join(" ", references).trim();
        if (joined.isEmpty()) {
            return null;
        }
        // References 为空白分隔的 Message-ID 链，末位即直接父邮件
        String[] tokens = joined.split("\\s+");
        String last = tokens[tokens.length - 1];
        return last.substring(0, Math.min(512, last.length()));
    }

    private InternetAddress[] toInternetAddresses(Address[] addresses) {
        if (addresses == null) {
            return new InternetAddress[0];
        }
        List<InternetAddress> list = new ArrayList<>();
        for (Address a : addresses) {
            if (a instanceof InternetAddress ia) {
                list.add(ia);
            }
        }
        return list.toArray(new InternetAddress[0]);
    }

    /** 地址数组转前端契约 JSON：[{"name":"...","email":"..."}] */
    private String addressesToJson(Address[] addresses) {
        InternetAddress[] list = toInternetAddresses(addresses);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String name = decodeText(list[i].getPersonal());
            String email = list[i].getAddress() == null ? "" : list[i].getAddress();
            sb.append("{\"name\":\"").append(escapeJson(name == null ? email : name))
                    .append("\",\"email\":\"").append(escapeJson(email)).append("\"}");
        }
        return sb.append(']').toString();
    }

    private Address[] toAddresses(List<ComposeMail.MailAddress> list) throws MessagingException {
        List<Address> result = new ArrayList<>();
        for (ComposeMail.MailAddress item : list) {
            result.add(inetAddress(item.email(), item.name() == null ? "" : item.name()));
        }
        return result.toArray(new Address[0]);
    }

    /** UTF-8 为 JVM 必备字符集，UnsupportedEncodingException 实际不可达，包装为 MessagingException */
    private InternetAddress inetAddress(String email, String personal) throws MessagingException {
        try {
            return new InternetAddress(email, personal, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("UTF-8 charset unavailable", e);
        }
    }

    private String decodeText(String text) {
        if (text == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(text);
        } catch (Exception e) {
            return text;
        }
    }

    private String safeSubject(Message msg) {
        try {
            return msg.getSubject();
        } catch (Exception e) {
            return "<unknown>";
        }
    }

    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /** MIME 树遍历器：提取 html/text 正文 + 附件元数据 */
    private static class ContentExtractor {
        String html;
        String text;
        boolean hasAttachment;
        final List<Map<String, Object>> attachments = new ArrayList<>();

        void walk(Part part) throws Exception {
            if (part.isMimeType("text/html") && html == null && !isAttachment(part)) {
                html = readContent(part);
                return;
            }
            if (part.isMimeType("text/plain") && text == null && !isAttachment(part)) {
                text = readContent(part);
                return;
            }
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    walk(mp.getBodyPart(i));
                }
                return;
            }
            if (isAttachment(part)) {
                hasAttachment = true;
                String name = part.getFileName();
                if (name != null) {
                    name = MimeUtility.decodeText(name);
                }
                // 附件元数据统一走 Jackson 序列化：文件名含双引号/反斜杠/控制字符，
                // 或 Content-Type 头带引号参数时，手拼转义不全会产生非法 JSON，
                // 导致读侧 parseAttachments 抛 IllegalStateException（ST37-O3）
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name == null ? "attachment" : name);
                item.put("size", Math.max(0, part.getSize()));
                item.put("contentType", String.valueOf(part.getContentType()));
                attachments.add(item);
            }
        }

        private boolean isAttachment(Part part) throws MessagingException {
            // 与附件下载回源（MailMimeSupport.collectAttachments）同口径，保证下标对齐
            return MailMimeSupport.isAttachment(part);
        }

        private String readContent(Part part) throws Exception {
            Object content = part.getContent();
            if (content instanceof String s) {
                return s;
            }
            if (content instanceof InputStream in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return String.valueOf(content);
        }

        String attachmentsJson() {
            try {
                return MAPPER.writeValueAsString(attachments);
            } catch (JsonProcessingException e) {
                // Map 值均为 String/int，序列化按构造不可失败；防御性包装与 MailMessageService.writeJson 同口径
                throw new IllegalStateException("附件元数据 JSON 序列化失败", e);
            }
        }
    }

    /** 解析出的邮件数据（同步落库用） */
    @lombok.Data
    public static class ParsedMail {
        private String imapUid;
        private String messageId;
        private String inReplyTo;
        private String subject;
        private String fromName;
        private String fromEmail;
        private String toJson;
        private String ccJson;
        private String preview;
        private String contentHtml;
        private String contentText;
        private Date sendDate;
        private String readStatus;
        private boolean starred;
        private int mailSize;
        private int hasAttachment;
        private String attachmentsJson;
    }

    /** 写信数据（发送用；inReplyTo=父邮件 Message-ID，回复场景携带，用于外发 In-Reply-To/References 头） */
    public record ComposeMail(
            List<MailAddress> to,
            List<MailAddress> cc,
            List<MailAddress> bcc,
            String subject,
            String htmlContent,
            String inReplyTo) {
        public record MailAddress(String name, String email) {
        }
    }

    /** 待发送的本地附件（token 已解析落盘路径，归属已校验） */
    public record ResolvedMailFile(String name, String contentType, java.nio.file.Path path) {
    }

    /** IMAP 回源提取的附件内容 */
    public record FetchedAttachment(byte[] content, String contentType, String fileName) {
    }
}
