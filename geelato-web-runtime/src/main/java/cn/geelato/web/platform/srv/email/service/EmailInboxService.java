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
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.SortTerm;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Service
@Slf4j
public class EmailInboxService {

    public record PageResult<T>(List<T> data, long total) {
    }

    public record ContactAddressRecord(EmailAddressDto address, Date sentAt, Date receivedAt) {
    }

    public List<EmailFolderDto> listFolders(String userId, String emailAccountId, String pattern) throws Exception {
        EmailAccountConfig config = getEmailAccountConfig(userId, emailAccountId);
        Store store = null;
        Folder root = null;
        try {
            log.debug("imap listFolders start, userId={}, emailAccountId={}, pattern={}", userId, config.id(), pattern);
            store = connect(config);
            root = store.getDefaultFolder();
            List<EmailFolderDto> tree;
            if (Strings.isNotBlank(pattern) && !isHierarchyWildcard(pattern)) {
                Folder folder = store.getFolder(pattern);
                if (folder == null || !folder.exists()) {
                    log.debug("imap listFolders target folder missing, userId={}, emailAccountId={}, pattern={}", userId, config.id(), pattern);
                    return Collections.emptyList();
                }
                tree = Collections.singletonList(buildFolderNode(folder, resolveParentFullName(folder), new HashSet<>()));
            } else {
                tree = buildFolderTree(root, null, new HashSet<>());
            }
            if (tree.isEmpty()) {
                log.debug("imap listFolders empty, userId={}, emailAccountId={}, pattern={}", userId, config.id(), pattern);
            }
            log.debug("imap listFolders done, userId={}, emailAccountId={}, size={}", userId, config.id(), countFolderNodes(tree));
            return tree;
        } finally {
            closeQuietly(root);
            closeQuietly(store);
        }
    }

    private List<EmailFolderDto> buildFolderTree(Folder parentFolder, String parentFullName, Set<String> visited) throws Exception {
        Folder[] folders = listDirectChildren(parentFolder);
        if (folders == null || folders.length == 0) {
            return Collections.emptyList();
        }
        List<EmailFolderDto> list = new ArrayList<>(folders.length);
        for (Folder folder : folders) {
            EmailFolderDto dto = buildFolderNode(folder, parentFullName, visited);
            if (dto != null) {
                list.add(dto);
            }
        }
        return list;
    }

    private EmailFolderDto buildFolderNode(Folder folder, String parentFullName, Set<String> visited) throws Exception {
        if (folder == null) {
            return null;
        }
        String fullName = folder.getFullName();
        String folderKey = Strings.isNotBlank(fullName) ? fullName : folder.getName();
        if (Strings.isBlank(folderKey) || !visited.add(folderKey)) {
            return null;
        }
        int type = safeFolderType(folder);
        EmailFolderDto dto = new EmailFolderDto();
        dto.setName(folder.getName());
        dto.setFullName(fullName);
        dto.setParentFullName(parentFullName);
        dto.setHoldsMessages((type & Folder.HOLDS_MESSAGES) != 0);
        dto.setHoldsFolders((type & Folder.HOLDS_FOLDERS) != 0);
        if (dto.getHoldsFolders()) {
            dto.setChildren(buildFolderTree(folder, fullName, visited));
        } else {
            dto.setChildren(Collections.emptyList());
        }
        return dto;
    }

    private Folder[] listDirectChildren(Folder folder) throws Exception {
        if (folder == null) {
            return new Folder[0];
        }
        Folder[] folders = folder.list("%");
        if ((folders == null || folders.length == 0) && isDefaultRootFolder(folder)) {
            folders = folder.list("*");
        }
        return folders != null ? folders : new Folder[0];
    }

    static boolean isDefaultRootFolder(Folder folder) {
        try {
            return folder != null && Strings.isBlank(folder.getFullName());
        } catch (Exception ex) {
            return false;
        }
    }

    static boolean isHierarchyWildcard(String pattern) {
        return "*".equals(pattern) || "%".equals(pattern);
    }

    static int safeFolderType(Folder folder) {
        try {
            return folder.getType();
        } catch (Exception ex) {
            return 0;
        }
    }

    static int countFolderNodes(List<EmailFolderDto> folders) {
        if (folders == null || folders.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (EmailFolderDto folder : folders) {
            if (folder == null) {
                continue;
            }
            count++;
            count += countFolderNodes(folder.getChildren());
        }
        return count;
    }

    static String resolveParentFullName(Folder folder) {
        if (folder == null) {
            return null;
        }
        try {
            String fullName = folder.getFullName();
            char separator = folder.getSeparator();
            if (Strings.isBlank(fullName) || separator == 0) {
                return null;
            }
            int idx = fullName.lastIndexOf(separator);
            if (idx <= 0) {
                return null;
            }
            return fullName.substring(0, idx);
        } catch (Exception ex) {
            return null;
        }
    }

    public PageResult<EmailMessageListItemDto> pageQuery(String userId, String emailAccountId, String folderName, int pageNum, int pageSize, Boolean unreadOnly) throws Exception {
        EmailAccountConfig config = getEmailAccountConfig(userId, emailAccountId);
        String folder = Strings.isNotBlank(folderName) ? folderName : config.defaultFolder();

        Store store = null;
        Folder imapFolder = null;
        try {
            log.debug("imap pageQuery start, userId={}, emailAccountId={}, folder={}, pageNum={}, pageSize={}, unreadOnly={}",
                    userId, config.id(), folder, pageNum, pageSize, unreadOnly);
            store = connect(config);
            imapFolder = store.getFolder(folder);
            imapFolder.open(Folder.READ_ONLY);

            int safePageNum = Math.max(1, pageNum);
            int safePageSize = Math.max(1, pageSize);
            long uidValidity = resolveUidValidity(imapFolder);

            record SortableMessage(long uid, Date sortDate, EmailMessageListItemDto dto) {
            }

            Message[] slice;
            long total;
            if (imapFolder instanceof IMAPFolder imap) {
                try {
                    SortTerm[] terms = new SortTerm[]{SortTerm.REVERSE, SortTerm.ARRIVAL};
                    SearchTerm searchTerm = Boolean.TRUE.equals(unreadOnly) ? new FlagTerm(new Flags(Flags.Flag.SEEN), false) : null;
                    Message[] sorted = searchTerm != null ? imap.getSortedMessages(terms, searchTerm) : imap.getSortedMessages(terms);
                    total = sorted != null ? sorted.length : 0;
                    int offset = (safePageNum - 1) * safePageSize;
                    if (offset >= total) {
                        log.debug("imap pageQuery out-of-range, userId={}, emailAccountId={}, folder={}, total={}, pageNum={}, pageSize={}",
                                userId, config.id(), folder, total, pageNum, pageSize);
                        return new PageResult<>(Collections.emptyList(), total);
                    }
                    int to = (int) Math.min((long) offset + safePageSize, total);
                    slice = Arrays.copyOfRange(sorted, offset, to);
                    log.debug("imap pageQuery loaded(sorted), userId={}, emailAccountId={}, folder={}, total={}, range=[{},{}), uidValidity={}",
                            userId, config.id(), folder, total, offset, to, uidValidity);
                } catch (Exception sortEx) {
                    int mc = imapFolder.getMessageCount();
                    if (mc <= 0) {
                        log.debug("imap pageQuery empty, userId={}, emailAccountId={}, folder={}", userId, config.id(), folder);
                        return new PageResult<>(Collections.emptyList(), 0);
                    }
                    int end = mc - (safePageNum - 1) * safePageSize;
                    int start = Math.max(1, end - safePageSize + 1);
                    if (end < 1) {
                        log.debug("imap pageQuery out-of-range, userId={}, emailAccountId={}, folder={}, total={}, pageNum={}, pageSize={}",
                                userId, config.id(), folder, mc, pageNum, pageSize);
                        return new PageResult<>(Collections.emptyList(), mc);
                    }
                    if (end > mc) {
                        end = mc;
                    }
                    Message[] messages = imapFolder.getMessages(start, end);
                    total = mc;
                    slice = messages;
                    log.debug("imap pageQuery loaded(fallback), userId={}, emailAccountId={}, folder={}, total={}, range=[{},{}], uidValidity={}, reason={}",
                            userId, config.id(), folder, mc, start, end, uidValidity, sortEx.toString());
                }
            } else {
                int mc = imapFolder.getMessageCount();
                if (mc <= 0) {
                    log.debug("imap pageQuery empty, userId={}, emailAccountId={}, folder={}", userId, config.id(), folder);
                    return new PageResult<>(Collections.emptyList(), 0);
                }
                int end = mc - (safePageNum - 1) * safePageSize;
                int start = Math.max(1, end - safePageSize + 1);
                if (end < 1) {
                    log.debug("imap pageQuery out-of-range, userId={}, emailAccountId={}, folder={}, total={}, pageNum={}, pageSize={}",
                            userId, config.id(), folder, mc, pageNum, pageSize);
                    return new PageResult<>(Collections.emptyList(), mc);
                }
                if (end > mc) {
                    end = mc;
                }
                slice = imapFolder.getMessages(start, end);
                total = mc;
                log.debug("imap pageQuery loaded, userId={}, emailAccountId={}, folder={}, total={}, range=[{},{}], uidValidity={}",
                        userId, config.id(), folder, mc, start, end, uidValidity);
            }

            List<SortableMessage> tmp = new ArrayList<>();
            for (Message msg : slice) {
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
            log.debug("imap pageQuery done, userId={}, emailAccountId={}, folder={}, returned={}, total={}",
                    userId, config.id(), folder, items.size(), total);
            return new PageResult<>(items, total);
        } finally {
            closeQuietly(imapFolder);
            closeQuietly(store);
        }
    }

    public EmailMessageDetailDto getMessageDetail(String userId, String mailId) throws Exception {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);
        EmailAccountConfig config = getEmailAccountConfig(userId, key.emailAccountId());

        Store store = null;
        Folder imapFolder = null;
        try {
            log.debug("imap getMessageDetail start, userId={}, emailAccountId={}, folder={}, uid={}",
                    userId, config.id(), key.folder(), key.uid());
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
            log.debug("imap getMessageDetail done, userId={}, emailAccountId={}, folder={}, uid={}, attachments={}",
                    userId, config.id(), key.folder(), key.uid(), dto.getAttachments() != null ? dto.getAttachments().size() : 0);
            return dto;
        } finally {
            closeQuietly(imapFolder);
            closeQuietly(store);
        }
    }

    public List<EmailAttachmentDto> getAttachments(String userId, String mailId) throws Exception {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);
        EmailAccountConfig config = getEmailAccountConfig(userId, key.emailAccountId());

        Store store = null;
        Folder imapFolder = null;
        try {
            log.debug("imap getAttachments start, userId={}, emailAccountId={}, folder={}, uid={}",
                    userId, config.id(), key.folder(), key.uid());
            store = connect(config);
            imapFolder = store.getFolder(key.folder());
            imapFolder.open(Folder.READ_ONLY);

            Message msg = resolveMessageByUid(imapFolder, key.uid());
            if (msg == null) {
                throw new IllegalArgumentException("mail not found");
            }

            MessageContent content = new MessageContent();
            parsePart(msg, content);
            List<EmailAttachmentDto> list = toAttachmentDtos(content.attachments, mailId);
            log.debug("imap getAttachments done, userId={}, emailAccountId={}, folder={}, uid={}, attachments={}",
                    userId, config.id(), key.folder(), key.uid(), list != null ? list.size() : 0);
            return list;
        } finally {
            closeQuietly(imapFolder);
            closeQuietly(store);
        }
    }

    public List<ContactAddressRecord> collectContactAddresses(String userId, String emailAccountId, List<String> folders,
                                                              int messageLimit, boolean includeTo, boolean includeCc, boolean includeFrom) throws Exception {
        EmailAccountConfig config = getEmailAccountConfig(userId, emailAccountId);
        List<String> targets = folders == null || folders.isEmpty() ? List.of(config.defaultFolder()) : folders;
        int remaining = Math.max(1, messageLimit);
        List<ContactAddressRecord> list = new ArrayList<>();

        Store store = null;
        try {
            store = connect(config);
            for (String folderName : targets) {
                if (remaining <= 0 || Strings.isBlank(folderName)) {
                    break;
                }
                Folder folder = null;
                try {
                    folder = store.getFolder(folderName);
                    if (folder == null || !folder.exists()) {
                        continue;
                    }
                    folder.open(Folder.READ_ONLY);
                    int total = folder.getMessageCount();
                    if (total <= 0) {
                        continue;
                    }
                    int scanCount = Math.min(total, remaining);
                    int start = Math.max(1, total - scanCount + 1);
                    Message[] messages = folder.getMessages(start, total);
                    for (int i = messages.length - 1; i >= 0 && remaining > 0; i--) {
                        Message msg = messages[i];
                        Date sentAt = extractSentDate(msg);
                        Date receivedAt = extractReceivedDate(msg);
                        if (includeFrom) {
                            EmailAddressDto from = firstAddress(msg.getFrom());
                            if (from != null && Strings.isNotBlank(from.getAddress())) {
                                list.add(new ContactAddressRecord(from, null, receivedAt != null ? receivedAt : sentAt));
                            }
                        }
                        if (includeTo) {
                            appendContactAddresses(list, addressList(msg.getRecipients(Message.RecipientType.TO)), sentAt, null);
                        }
                        if (includeCc) {
                            appendContactAddresses(list, addressList(msg.getRecipients(Message.RecipientType.CC)), sentAt, null);
                        }
                        remaining--;
                    }
                } finally {
                    closeQuietly(folder);
                }
            }
            return list;
        } finally {
            closeQuietly(store);
        }
    }

    public boolean validateAccount(String userId, String emailAccountId) throws Exception {
        EmailAccountConfig config = getEmailAccountConfig(userId, emailAccountId, false);
        Store store = null;
        Folder root = null;
        try {
            log.debug("imap validateAccount start, userId={}, emailAccountId={}", userId, config.id());
            store = connect(config);
            root = store.getDefaultFolder();
            root.list("*");
            log.debug("imap validateAccount done, userId={}, emailAccountId={}", userId, config.id());
            return true;
        } finally {
            closeQuietly(root);
            closeQuietly(store);
        }
    }

    public record DownloadAttachment(String fileName, String contentType, InputStream inputStream) {
    }

    public DownloadAttachment openAttachmentStream(String userId, String mailId, String partId) throws Exception {
        MailIdCodec.MailKey key = MailIdCodec.decode(mailId);
        EmailAccountConfig config = getEmailAccountConfig(userId, key.emailAccountId());

        Store store = connect(config);
        Folder imapFolder = null;
        try {
            log.debug("imap openAttachmentStream start, userId={}, emailAccountId={}, folder={}, uid={}, partId={}",
                    userId, config.id(), key.folder(), key.uid(), partId);
            imapFolder = store.getFolder(key.folder());
            imapFolder.open(Folder.READ_ONLY);

            Message msg = resolveMessageByUid(imapFolder, key.uid());
            if (msg == null) {
                throw new IllegalArgumentException("mail not found");
            }
            List<Part> parts = listAttachmentParts(msg);
            log.debug("imap openAttachmentStream scanned, userId={}, emailAccountId={}, folder={}, uid={}, attachments={}",
                    userId, config.id(), key.folder(), key.uid(), parts.size());
            Part part = findAttachmentPart(parts, partId);
            if (part == null) {
                throw new IllegalArgumentException("attachment not found");
            }
            String fileName = resolveFileName(part);
            String contentType = safeContentType(part.getContentType());
            InputStream is;
            try {
                is = part.getInputStream();
            } catch (Exception ex) {
                log.debug("imap openAttachmentStream getInputStream failed, userId={}, emailAccountId={}, folder={}, uid={}, partId={}, fileName={}, contentType={}, disposition={}",
                        userId, config.id(), key.folder(), key.uid(), partId, fileName, contentType, part.getDisposition());
                throw ex;
            }
            log.debug("imap openAttachmentStream ready, userId={}, emailAccountId={}, folder={}, uid={}, partId={}, fileName={}, contentType={}",
                    userId, config.id(), key.folder(), key.uid(), partId, fileName, contentType);
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

    EmailAccountConfig getEmailAccountConfig(String userId, String emailAccountId) {
        return getEmailAccountConfig(userId, emailAccountId, true);
    }

    EmailAccountConfig getEmailAccountConfig(String userId, String emailAccountId, boolean enabledOnly) {
        Map<String, Object> row;
        if (Strings.isNotBlank(emailAccountId)) {
            List<Filter> filters = new ArrayList<>();
            filters.add(Filter.eq("id", emailAccountId));
            filters.add(Filter.eq("userId", userId));
            filters.add(Filter.eq("delStatus", 0));
            if (enabledOnly) {
                filters.add(Filter.eq("enableStatus", 1));
            }
            row = MetaFactory.query(UserEmailAccount.class)
                    .where(filters.toArray(new Filter[0]))
                    .one();
        } else {
            row = findFirstEmailAccountRow(userId, enabledOnly);
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

    Map<String, Object> findFirstEmailAccountRow(String userId, boolean enabledOnly) {
        List<Filter> defaultFilters = new ArrayList<>();
        defaultFilters.add(Filter.eq("userId", userId));
        defaultFilters.add(Filter.eq("delStatus", 0));
        defaultFilters.add(Filter.eq("defaultFlag", 1));
        if (enabledOnly) {
            defaultFilters.add(Filter.eq("enableStatus", 1));
        }

        cn.geelato.orm.PageResult<Map<String, Object>> defaultPage = MetaFactory.query(UserEmailAccount.class)
                .where(defaultFilters.toArray(new Filter[0]))
                .order(Order.desc("createAt"))
                .page(1, 1)
                .page();
        if (defaultPage != null && defaultPage.getRecords() != null && !defaultPage.getRecords().isEmpty()) {
            return defaultPage.getRecords().get(0);
        }

        List<Filter> fallbackFilters = new ArrayList<>();
        fallbackFilters.add(Filter.eq("userId", userId));
        fallbackFilters.add(Filter.eq("delStatus", 0));
        if (enabledOnly) {
            fallbackFilters.add(Filter.eq("enableStatus", 1));
        }

        cn.geelato.orm.PageResult<Map<String, Object>> fallbackPage = MetaFactory.query(UserEmailAccount.class)
                .where(fallbackFilters.toArray(new Filter[0]))
                .order(Order.desc("defaultFlag"), Order.desc("createAt"))
                .page(1, 1)
                .page();
        if (fallbackPage == null || fallbackPage.getRecords() == null || fallbackPage.getRecords().isEmpty()) {
            return null;
        }
        return fallbackPage.getRecords().get(0);
    }

    Store connect(EmailAccountConfig config) throws Exception {
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
        applyImapId(store, config.id());
        return store;
    }

    void applyImapId(Store store, String emailAccountId) {
        try {
            if (!(store instanceof IMAPStore imapStore)) {
                log.debug("imap id skipped, emailAccountId={}, storeClass={}", emailAccountId, store != null ? store.getClass().getName() : null);
                return;
            }
            // Send IMAP ID to reduce the chance of provider-side security interception.
            Map<String, String> idInfo = new java.util.HashMap<>();
            idInfo.put("name", "Geelato Platform");
            imapStore.id(idInfo);
            log.debug("imap id applied, emailAccountId={}, name={}", emailAccountId, idInfo.get("name"));
        } catch (Exception ex) {
            log.warn("imap id apply failed, emailAccountId={}", emailAccountId, ex);
        }
    }

    static Message resolveMessageByUid(Folder folder, long uid) throws Exception {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder.getMessageByUID(uid);
        }
        return null;
    }

    static long resolveUid(Folder folder, Message msg) throws Exception {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder.getUID(msg);
        }
        return msg.getMessageNumber();
    }

    static long resolveUidValidity(Folder folder) throws MessagingException {
        if (folder instanceof IMAPFolder imapFolder) {
            return imapFolder.getUIDValidity();
        }
        return 0;
    }

    static Date extractSentDate(Message msg) throws Exception {
        Date d = msg.getSentDate();
        if (d != null) {
            return d;
        }
        if (msg.getReceivedDate() != null) {
            return msg.getReceivedDate();
        }
        return null;
    }

    static Date extractReceivedDate(Message msg) throws Exception {
        return msg.getReceivedDate();
    }

    static Long extractSize(Message msg) {
        try {
            int size = msg.getSize();
            return size >= 0 ? (long) size : null;
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean guessHasAttachments(Message msg) {
        try {
            return !listAttachmentParts(msg).isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }

    static String extractMessageId(Message msg) {
        if (msg instanceof MimeMessage mm) {
            try {
                return mm.getMessageID();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static EmailAddressDto firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        return toEmailAddress(addresses[0]);
    }

    static List<EmailAddressDto> addressList(Address[] addresses) {
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

    static EmailAddressDto toEmailAddress(Address address) {
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

    static void appendContactAddresses(List<ContactAddressRecord> out, List<EmailAddressDto> addresses, Date sentAt, Date receivedAt) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        for (EmailAddressDto address : addresses) {
            if (address == null || Strings.isBlank(address.getAddress())) {
                continue;
            }
            out.add(new ContactAddressRecord(address, sentAt, receivedAt));
        }
    }

    static String safeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int idx = contentType.indexOf(';');
        if (idx > 0) {
            return contentType.substring(0, idx).trim();
        }
        return contentType.trim();
    }

    static void closeQuietly(Folder folder) {
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

    static void closeQuietly(Store store) {
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

    static class MessageContent {
        private String textBody;
        private String htmlBody;
        private final List<Part> attachments = new ArrayList<>();

        public String getTextBody() {
            return textBody;
        }

        public String getHtmlBody() {
            return htmlBody;
        }

        public List<Part> getAttachments() {
            return attachments;
        }
    }

    static void parsePart(Part part, MessageContent out) throws Exception {
        if (isAttachment(part)) {
            out.attachments.add(part);
            return;
        }

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

    static boolean isAttachment(Part part) throws Exception {
        String disposition = part.getDisposition();
        String fileName = resolveFileName(part);
        if (Strings.isNotBlank(fileName)) {
            if (Part.INLINE.equalsIgnoreCase(disposition) && isInlineImage(part)) {
                return false;
            }
            return true;
        }
        if (Strings.isBlank(disposition)) {
            return false;
        }
        return Part.ATTACHMENT.equalsIgnoreCase(disposition);
    }

    static boolean isInlineImage(Part part) {
        try {
            String ct = safeContentType(part.getContentType());
            if (ct == null || !ct.toLowerCase().startsWith("image/")) {
                return false;
            }
            String cid = extractHeader(part, "Content-ID");
            return Strings.isNotBlank(cid);
        } catch (Exception ex) {
            return false;
        }
    }

    static String resolveFileName(Part part) {
        if (part == null) {
            return null;
        }
        try {
            String fileName = decodeMimeText(part.getFileName());
            if (Strings.isNotBlank(fileName)) {
                return fileName;
            }
            String cd = extractHeader(part, "Content-Disposition");
            fileName = decodeMimeText(parseHeaderParam(cd, "filename*"));
            if (Strings.isNotBlank(fileName)) {
                return decodeRfc5987(fileName);
            }
            fileName = decodeMimeText(parseHeaderParam(cd, "filename"));
            if (Strings.isNotBlank(fileName)) {
                return fileName;
            }
            String ct = extractHeader(part, "Content-Type");
            fileName = decodeMimeText(parseHeaderParam(ct, "name"));
            return fileName;
        } catch (Exception ex) {
            return null;
        }
    }

    static String decodeRfc5987(String v) {
        if (Strings.isBlank(v)) {
            return v;
        }
        int idx = v.indexOf("''");
        if (idx < 0) {
            return v;
        }
        String enc = v.substring(idx + 2);
        try {
            return URLDecoder.decode(enc, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return v;
        }
    }

    static String parseHeaderParam(String header, String key) {
        if (Strings.isBlank(header) || Strings.isBlank(key)) {
            return null;
        }
        String lower = header.toLowerCase();
        String k = key.toLowerCase() + "=";
        int idx = lower.indexOf(k);
        if (idx < 0) {
            return null;
        }
        int start = idx + k.length();
        int end = header.indexOf(';', start);
        String raw = end >= 0 ? header.substring(start, end) : header.substring(start);
        String val = raw.trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    static List<EmailAttachmentDto> toAttachmentDtos(List<Part> parts, String mailId) throws Exception {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmailAttachmentDto> list = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            EmailAttachmentDto dto = new EmailAttachmentDto();
            String partId = String.valueOf(i + 1);
            dto.setPartId(partId);
            dto.setFileName(resolveFileName(p));
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

    static Long extractBodyPartSize(Part part) {
        try {
            int size = part.getSize();
            return size >= 0 ? (long) size : null;
        } catch (Exception ex) {
            return null;
        }
    }

    static String extractHeader(Part part, String headerName) throws Exception {
        String[] hs = part.getHeader(headerName);
        if (hs == null || hs.length == 0) {
            return null;
        }
        return hs[0];
    }

    static List<Part> listAttachmentParts(Part root) throws Exception {
        MessageContent content = new MessageContent();
        parsePart(root, content);
        return content.attachments;
    }

    static Part findAttachmentPart(List<Part> parts, String partId) {
        int target;
        try {
            target = Integer.parseInt(partId);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (target <= 0) {
            return null;
        }
        if (target > parts.size()) {
            return null;
        }
        return parts.get(target - 1);
    }

    static void collectAttachmentParts(Part part, List<Part> out) throws Exception {
        if (isAttachment(part)) {
            out.add(part);
            return;
        }
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
        }
    }

    static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static Integer intVal(Object o) {
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

    static int intVal0(Object o) {
        Integer v = intVal(o);
        return v != null ? v : 0;
    }

    static String decodeMimeText(String raw) {
        if (Strings.isBlank(raw)) {
            return raw;
        }
        try {
            return MimeUtility.decodeText(raw);
        } catch (Exception ex) {
            return raw;
        }
    }

    static String maskEmail(String raw) {
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

    record EmailAccountConfig(
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
