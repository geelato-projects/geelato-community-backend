package cn.geelato.web.platform.srv.email.service;

import cn.geelato.core.util.EncryptUtils;
import cn.geelato.meta.UserEmailAccount;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import cn.geelato.web.platform.srv.email.MailIdCodec;
import cn.geelato.web.platform.srv.email.dto.EmailAddressDto;
import cn.geelato.web.platform.srv.email.dto.EmailAttachmentDto;
import cn.geelato.web.platform.srv.email.dto.EmailFolderDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageDetailDto;
import cn.geelato.web.platform.srv.email.dto.EmailMessageListItemDto;
import com.sun.mail.imap.IMAPFolder;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@Slf4j
public class EmailInboxService {

    public record PageResult<T>(List<T> data, long total) {
    }

    public List<EmailFolderDto> listFolders(String tenantCode, String userId, String emailAccountId, String pattern) throws Exception {
        EmailAccountConfig config = getEmailAccountConfig(tenantCode, userId, emailAccountId);
        String p = Strings.isNotBlank(pattern) ? pattern : "*";

        Store store = null;
        Folder root = null;
        try {
            log.debug("imap listFolders start, tenantCode={}, userId={}, emailAccountId={}, pattern={}", tenantCode, userId, config.id(), p);
            store = connect(config);
            root = store.getDefaultFolder();
            Folder[] folders = root.list(p);
            if (folders == null || folders.length == 0) {
                log.debug("imap listFolders empty, tenantCode={}, userId={}, emailAccountId={}, pattern={}", tenantCode, userId, config.id(), p);
                return Collections.emptyList();
            }
            List<EmailFolderDto> list = new ArrayList<>(folders.length);
            for (Folder f : folders) {
                if (f == null) {
                    continue;
                }
                int type;
                try {
                    type = f.getType();
                } catch (Exception ex) {
                    type = 0;
                }
                EmailFolderDto dto = new EmailFolderDto();
                dto.setName(f.getName());
                dto.setFullName(f.getFullName());
                dto.setHoldsMessages((type & Folder.HOLDS_MESSAGES) != 0);
                dto.setHoldsFolders((type & Folder.HOLDS_FOLDERS) != 0);
                list.add(dto);
            }
            log.debug("imap listFolders done, tenantCode={}, userId={}, emailAccountId={}, size={}", tenantCode, userId, config.id(), list.size());
            return list;
        } finally {
            closeQuietly(root);
            closeQuietly(store);
        }
    }

    public PageResult<EmailMessageListItemDto> pageQuery(String tenantCode, String userId, String emailAccountId, String folderName, int pageNum, int pageSize, Boolean unreadOnly) throws Exception {
        EmailAccountConfig config = getEmailAccountConfig(tenantCode, userId, emailAccountId);
        String folder = Strings.isNotBlank(folderName) ? folderName : config.defaultFolder();

        Store store = null;
        Folder imapFolder = null;
        try {
            log.debug("imap pageQuery start, tenantCode={}, userId={}, emailAccountId={}, folder={}, pageNum={}, pageSize={}, unreadOnly={}",
                    tenantCode, userId, config.id(), folder, pageNum, pageSize, unreadOnly);
            store = connect(config);
            imapFolder = store.getFolder(folder);
            imapFolder.open(Folder.READ_ONLY);

            int total = imapFolder.getMessageCount();
            if (total <= 0) {
                log.debug("imap pageQuery empty, tenantCode={}, userId={}, emailAccountId={}, folder={}", tenantCode, userId, config.id(), folder);
                return new PageResult<>(Collections.emptyList(), 0);
            }

            int safePageNum = Math.max(1, pageNum);
            int safePageSize = Math.max(1, pageSize);
            int end = total - (safePageNum - 1) * safePageSize;
            int start = Math.max(1, end - safePageSize + 1);
            if (end < 1) {
                log.debug("imap pageQuery out-of-range, tenantCode={}, userId={}, emailAccountId={}, folder={}, total={}, pageNum={}, pageSize={}",
                        tenantCode, userId, config.id(), folder, total, pageNum, pageSize);
                return new PageResult<>(Collections.emptyList(), total);
            }
            if (end > total) {
                end = total;
            }

            Message[] messages = imapFolder.getMessages(start, end);
            long uidValidity = resolveUidValidity(imapFolder);
            log.debug("imap pageQuery loaded, tenantCode={}, userId={}, emailAccountId={}, folder={}, total={}, range=[{},{}], uidValidity={}",
                    tenantCode, userId, config.id(), folder, total, start, end, uidValidity);

            record SortableMessage(long uid, Date sortDate, EmailMessageListItemDto dto) {
            }

            List<SortableMessage> tmp = new ArrayList<>();
            for (Message msg : messages) {
                boolean unread = !msg.isSet(Flags.Flag.SEEN);
                if (Boolean.TRUE.equals(unreadOnly) && !unread) {
                    continue;
                }
                EmailMessageListItemDto dto = new EmailMessageListItemDto();
                long uid = resolveUid(imapFolder, msg);
                dto.setId(MailIdCodec.encode(new MailIdCodec.MailKey(config.id(), folder, uidValidity, uid)));
                dto.setEmailAccountId(config.id());
                dto.setFolder(folder);
                dto.setSubject(msg.getSubject());
                dto.setFrom(firstAddress(msg.getFrom()));
                dto.setTo(addressList(msg.getRecipients(Message.RecipientType.TO)));
                dto.setCc(addressList(msg.getRecipients(Message.RecipientType.CC)));
                Date sentAt = extractSentDate(msg);
                Date receivedAt = extractReceivedDate(msg);
                dto.setSentAt(sentAt);
                dto.setReceivedAt(receivedAt);
                dto.setSize(extractSize(msg));
                dto.setUnread(unread);
                dto.setHasAttachments(guessHasAttachments(msg));
                dto.setSnippet(null);
                tmp.add(new SortableMessage(uid, receivedAt != null ? receivedAt : sentAt, dto));
            }

            tmp.sort((a, b) -> {
                long ta = a.sortDate() != null ? a.sortDate().getTime() : Long.MIN_VALUE;
                long tb = b.sortDate() != null ? b.sortDate().getTime() : Long.MIN_VALUE;
                int c = Long.compare(tb, ta);
                if (c != 0) {
                    return c;
                }
                return Long.compare(b.uid(), a.uid());
            });

            List<EmailMessageListItemDto> items = tmp.stream().map(SortableMessage::dto).toList();
            log.debug("imap pageQuery done, tenantCode={}, userId={}, emailAccountId={}, folder={}, returned={}, total={}",
                    tenantCode, userId, config.id(), folder, items.size(), total);
            return new PageResult<>(items, total);
        } finally {
            closeQuietly(imapFolder);
            closeQuietly(store);
        }
    }

    public EmailMessageDetailDto getMessageDetail(String tenantCode, String userId, String mailId) throws Exception {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);
        EmailAccountConfig config = getEmailAccountConfig(tenantCode, userId, key.emailAccountId());

        Store store = null;
        Folder imapFolder = null;
        try {
            log.debug("imap getMessageDetail start, tenantCode={}, userId={}, emailAccountId={}, folder={}, uid={}",
                    tenantCode, userId, config.id(), key.folder(), key.uid());
            store = connect(config);
            imapFolder = store.getFolder(key.folder());
            imapFolder.open(Folder.READ_ONLY);

            Message msg = resolveMessageByUid(imapFolder, key.uid());
            if (msg == null) {
                throw new IllegalArgumentException("mail not found");
            }

            EmailMessageDetailDto dto = new EmailMessageDetailDto();
            dto.setId(mailId);
            dto.setEmailAccountId(config.id());
            dto.setFolder(key.folder());
            dto.setSubject(msg.getSubject());
            dto.setFrom(firstAddress(msg.getFrom()));
            dto.setTo(addressList(msg.getRecipients(Message.RecipientType.TO)));
            dto.setCc(addressList(msg.getRecipients(Message.RecipientType.CC)));
            dto.setBcc(addressList(msg.getRecipients(Message.RecipientType.BCC)));
            dto.setSentAt(extractSentDate(msg));
            dto.setReceivedAt(extractReceivedDate(msg));
            dto.setMessageId(extractMessageId(msg));

            MessageContent content = new MessageContent();
            parsePart(msg, content);
            dto.setTextBody(content.textBody);
            dto.setHtmlBody(content.htmlBody);
            dto.setAttachments(toAttachmentDtos(content.attachments, mailId));
            log.debug("imap getMessageDetail done, tenantCode={}, userId={}, emailAccountId={}, folder={}, uid={}, attachments={}",
                    tenantCode, userId, config.id(), key.folder(), key.uid(), dto.getAttachments() != null ? dto.getAttachments().size() : 0);
            return dto;
        } finally {
            closeQuietly(imapFolder);
            closeQuietly(store);
        }
    }

    public record DownloadAttachment(String fileName, String contentType, InputStream inputStream) {
    }

    public DownloadAttachment openAttachmentStream(String tenantCode, String userId, String mailId, String partId) throws Exception {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);
        EmailAccountConfig config = getEmailAccountConfig(tenantCode, userId, key.emailAccountId());

        Store store = connect(config);
        Folder imapFolder = null;
        try {
            log.debug("imap openAttachmentStream start, tenantCode={}, userId={}, emailAccountId={}, folder={}, uid={}, partId={}",
                    tenantCode, userId, config.id(), key.folder(), key.uid(), partId);
            imapFolder = store.getFolder(key.folder());
            imapFolder.open(Folder.READ_ONLY);

            Message msg = resolveMessageByUid(imapFolder, key.uid());
            if (msg == null) {
                throw new IllegalArgumentException("mail not found");
            }
            MimeBodyPart part = findAttachmentPart(msg, partId);
            if (part == null) {
                throw new IllegalArgumentException("attachment not found");
            }
            String fileName = part.getFileName();
            String contentType = safeContentType(part.getContentType());
            InputStream is = part.getInputStream();
            log.debug("imap openAttachmentStream ready, tenantCode={}, userId={}, emailAccountId={}, folder={}, uid={}, partId={}, fileName={}, contentType={}",
                    tenantCode, userId, config.id(), key.folder(), key.uid(), partId, fileName, contentType);
            return new DownloadAttachment(fileName, contentType, new StoreBoundInputStream(is, imapFolder, store));
        } catch (Exception ex) {
            closeQuietly(imapFolder);
            closeQuietly(store);
            throw ex;
        }
    }

    private static final class StoreBoundInputStream extends InputStream {
        private final InputStream delegate;
        private final Folder folder;
        private final Store store;

        private StoreBoundInputStream(InputStream delegate, Folder folder, Store store) {
            this.delegate = delegate;
            this.folder = folder;
            this.store = store;
        }

        @Override
        public int read() throws java.io.IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b) throws java.io.IOException {
            return delegate.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws java.io.IOException {
            try {
                delegate.close();
            } finally {
                closeQuietly(folder);
                closeQuietly(store);
            }
        }
    }

    private EmailAccountConfig getEmailAccountConfig(String tenantCode, String userId, String emailAccountId) {
        Map<String, Object> row;
        if (Strings.isNotBlank(emailAccountId)) {
            row = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("id", emailAccountId),
                            Filter.eq("tenantCode", tenantCode),
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0),
                            Filter.eq("enableStatus", 1)
                    )
                    .one();
        } else {
            row = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("tenantCode", tenantCode),
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

        String authType = stringVal(row.get("authType"));
        if (Strings.isBlank(authType)) {
            authType = "auth_code";
        }
        if ("oauth2".equalsIgnoreCase(authType)) {
            throw new IllegalArgumentException("oauth2 not implemented");
        }

        String secretEnc = stringVal(row.get("authSecret"));
        String secret = Strings.isNotBlank(secretEnc) ? EncryptUtils.decrypt(secretEnc) : null;

        String host = stringVal(row.get("imapHost"));
        Integer port = intVal(row.get("imapPort"));
        int ssl = intVal0(row.get("imapSsl"));
        String folder = stringVal(row.get("imapFolderDefault"));
        if (Strings.isBlank(folder)) {
            folder = "INBOX";
        }
        String user = stringVal(row.get("authUser"));
        if (Strings.isBlank(user)) {
            user = stringVal(row.get("emailAddress"));
        }

        return new EmailAccountConfig(
                stringVal(row.get("id")),
                host,
                port != null ? port : (ssl == 1 ? 993 : 143),
                ssl == 1,
                folder,
                authType,
                user,
                secret
        );
    }

    private Store connect(EmailAccountConfig config) throws Exception {
        if (Strings.isBlank(config.host()) || config.port() <= 0 || Strings.isBlank(config.authUser()) || Strings.isBlank(config.authSecret())) {
            throw new IllegalArgumentException("email account config invalid");
        }

        log.debug("imap connect, emailAccountId={}, protocol={}, host={}, port={}, user={}",
                config.id(), config.ssl() ? "imaps" : "imap", config.host(), config.port(), maskEmail(config.authUser()));

        Properties props = new Properties();
        String protocol = config.ssl() ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        if (config.ssl()) {
            props.put("mail.imaps.ssl.enable", "true");
        }
        props.put("mail." + protocol + ".host", config.host());
        props.put("mail." + protocol + ".port", String.valueOf(config.port()));

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(config.host(), config.port(), config.authUser(), config.authSecret());
        return store;
    }

    private static Message resolveMessageByUid(Folder folder, long uid) throws Exception {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder.getMessageByUID(uid);
        }
        return null;
    }

    private static long resolveUid(Folder folder, Message msg) throws Exception {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder.getUID(msg);
        }
        return msg.getMessageNumber();
    }

    private static long resolveUidValidity(Folder folder) throws MessagingException {
        if (folder instanceof IMAPFolder imapFolder) {
            return imapFolder.getUIDValidity();
        }
        return 0;
    }

    private static Date extractSentDate(Message msg) throws Exception {
        Date d = msg.getSentDate();
        if (d != null) {
            return d;
        }
        if (msg.getReceivedDate() != null) {
            return msg.getReceivedDate();
        }
        return null;
    }

    private static Date extractReceivedDate(Message msg) throws Exception {
        return msg.getReceivedDate();
    }

    private static Long extractSize(Message msg) {
        try {
            int size = msg.getSize();
            return size >= 0 ? (long) size : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean guessHasAttachments(Message msg) {
        try {
            String ct = msg.getContentType();
            return ct != null && ct.toLowerCase().contains("multipart");
        } catch (Exception ex) {
            return false;
        }
    }

    private static String extractMessageId(Message msg) {
        if (msg instanceof MimeMessage mm) {
            try {
                return mm.getMessageID();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static EmailAddressDto firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return toEmailAddress(addresses[0]);
    }

    private static List<EmailAddressDto> addressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return Collections.emptyList();
        }
        List<EmailAddressDto> list = new ArrayList<>(addresses.length);
        for (Address a : addresses) {
            EmailAddressDto dto = toEmailAddress(a);
            if (dto != null) {
                list.add(dto);
            }
        }
        return list;
    }

    private static EmailAddressDto toEmailAddress(Address address) {
        if (address instanceof InternetAddress ia) {
            EmailAddressDto dto = new EmailAddressDto();
            dto.setName(ia.getPersonal());
            dto.setAddress(ia.getAddress());
            return dto;
        }
        if (address != null) {
            EmailAddressDto dto = new EmailAddressDto();
            dto.setAddress(address.toString());
            return dto;
        }
        return null;
    }

    private static String safeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int idx = contentType.indexOf(';');
        if (idx > 0) {
            return contentType.substring(0, idx).trim();
        }
        return contentType.trim();
    }

    private static void closeQuietly(Folder folder) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            if (store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static class MessageContent {
        private String textBody;
        private String htmlBody;
        private final List<MimeBodyPart> attachments = new ArrayList<>();
    }

    private static void parsePart(Part part, MessageContent out) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    parsePart((Part) bp, out);
                }
            }
            return;
        }

        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part) {
                parsePart((Part) content, out);
            }
            return;
        }

        if (isAttachment(part)) {
            if (part instanceof MimeBodyPart mbp) {
                out.attachments.add(mbp);
            }
            return;
        }

        if (part.isMimeType("text/plain") && out.textBody == null) {
            Object content = part.getContent();
            if (content instanceof String s) {
                out.textBody = s;
            }
            return;
        }
        if (part.isMimeType("text/html") && out.htmlBody == null) {
            Object content = part.getContent();
            if (content instanceof String s) {
                out.htmlBody = s;
            }
        }
    }

    private static boolean isAttachment(Part part) throws Exception {
        String disposition = part.getDisposition();
        String fileName = part.getFileName();
        if (Strings.isNotBlank(fileName)) {
            return true;
        }
        if (Strings.isBlank(disposition)) {
            return false;
        }
        return Part.ATTACHMENT.equalsIgnoreCase(disposition) || Part.INLINE.equalsIgnoreCase(disposition);
    }

    private static List<EmailAttachmentDto> toAttachmentDtos(List<MimeBodyPart> parts, String mailId) throws Exception {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmailAttachmentDto> list = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            MimeBodyPart p = parts.get(i);
            EmailAttachmentDto dto = new EmailAttachmentDto();
            String partId = String.valueOf(i + 1);
            dto.setPartId(partId);
            dto.setFileName(p.getFileName());
            dto.setContentType(safeContentType(p.getContentType()));
            dto.setSize(extractBodyPartSize(p));
            dto.setInline(Part.INLINE.equalsIgnoreCase(p.getDisposition()));
            dto.setContentId(extractHeader(p, "Content-ID"));
            dto.setDownloadUrl("/api/email/message/" + mailId + "/attachment/" + partId + "/download");
            dto.setSaveToOssUrl("/api/email/message/" + mailId + "/attachment/" + partId + "/save");
            list.add(dto);
        }
        return list;
    }

    private static Long extractBodyPartSize(MimeBodyPart part) {
        try {
            int size = part.getSize();
            return size >= 0 ? (long) size : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String extractHeader(Part part, String headerName) throws Exception {
        String[] hs = part.getHeader(headerName);
        if (hs == null || hs.length == 0) {
            return null;
        }
        return hs[0];
    }

    private static MimeBodyPart findAttachmentPart(Part root, String partId) throws Exception {
        int target;
        try {
            target = Integer.parseInt(partId);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (target <= 0) {
            return null;
        }
        List<MimeBodyPart> parts = new ArrayList<>();
        collectAttachmentParts(root, parts);
        if (target > parts.size()) {
            return null;
        }
        return parts.get(target - 1);
    }

    private static void collectAttachmentParts(Part part, List<MimeBodyPart> out) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    collectAttachmentParts((Part) bp, out);
                }
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part) {
                collectAttachmentParts((Part) content, out);
            }
            return;
        }
        if (isAttachment(part) && part instanceof MimeBodyPart mbp) {
            out.add(mbp);
        }
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ex) {
            return null;
        }
    }

    private static int intVal0(Object o) {
        Integer v = intVal(o);
        return v != null ? v : 0;
    }

    private static String maskEmail(String raw) {
        if (Strings.isBlank(raw)) {
            return raw;
        }
        int at = raw.indexOf('@');
        if (at <= 1) {
            return raw;
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private record EmailAccountConfig(
            String id,
            String host,
            int port,
            boolean ssl,
            String defaultFolder,
            String authType,
            String authUser,
            String authSecret
    ) {
    }
}
