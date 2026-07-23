package cn.geelato.web.platform.srv.email.service;

import cn.geelato.utils.UIDGenerator;
import cn.geelato.meta.Attachment;
import cn.geelato.meta.email.EmailAttachment;
import cn.geelato.meta.email.EmailMessage;
import cn.geelato.meta.email.EmailSyncLog;
import cn.geelato.meta.UserEmailAccount;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Order;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.email.service.EmailInboxService.EmailAccountConfig;
import cn.geelato.web.platform.srv.file.param.FileParam;
import cn.geelato.utils.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.*;
import jakarta.mail.UIDFolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件同步核心服务
 * <p>
 * 负责将 IMAP 服务器上的邮件增量同步到本地 PostgreSQL。
 * 通过 {@link EmailInboxService} 的 package-private 方法访问 IMAP 底层能力。
 * </p>
 */
@Service
@Slf4j
public class EmailSyncService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private EmailInboxService emailInboxService;

    @Autowired
    private FileHandler fileHandler;

    @Value("${geelato.email.sync.batch-size:50}")
    private int batchSize;

    @Value("${geelato.email.sync.default-interval-minutes:5}")
    private int defaultIntervalMinutes;

    /**
     * 简单防并发锁：邮箱账号ID -> 是否正在同步
     */
    private final Set<String> syncingAccountIds = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------ 对外接口

    /**
     * 同步指定邮箱账号的所有文件夹（异步执行）
     */
    public void syncAccount(String userId, String emailAccountId) {
        if (!syncingAccountIds.add(emailAccountId)) {
            log.info("邮箱账号 {} 正在同步中，跳过本次触发", emailAccountId);
            return;
        }
        // 先标记 MySQL 主库中的同步状态
        updateAccountSyncStatus(emailAccountId, "syncing");
        try {
            EmailAccountConfig config = emailInboxService.getEmailAccountConfig(userId, emailAccountId);
            Store store = emailInboxService.connect(config);
            try {
                Folder root = store.getDefaultFolder();
                syncFolderTree(userId, config, root, store);
            } finally {
                EmailInboxService.closeQuietly(store);
            }
            updateAccountSyncStatus(emailAccountId, "idle");
            updateAccountLastSyncAt(emailAccountId);
        } catch (Exception e) {
            log.error("邮箱账号 {} 同步失败", emailAccountId, e);
            updateAccountSyncStatus(emailAccountId, "error");
        } finally {
            syncingAccountIds.remove(emailAccountId);
        }
    }

    /**
     * 同步单个文件夹（供外部手动触发）
     */
    public EmailSyncLog syncSingleFolder(String userId, String emailAccountId, String folderName) throws Exception {
        EmailAccountConfig config = emailInboxService.getEmailAccountConfig(userId, emailAccountId);
        Store store = null;
        EmailSyncLog syncLog = null;
        try {
            store = emailInboxService.connect(config);
            Folder imapFolder = store.getFolder(folderName);
            if (imapFolder == null || !imapFolder.exists()) {
                throw new IllegalArgumentException("文件夹不存在: " + folderName);
            }
            syncLog = doSyncFolder(userId, config, imapFolder, folderName);
        } finally {
            EmailInboxService.closeQuietly(store);
        }
        return syncLog;
    }

    /**
     * 查询同步状态（简要）
     */
    public Map<String, Object> getSyncStatus(String userId, String emailAccountId) {
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("id", emailAccountId));
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        return MetaFactory.query(UserEmailAccount.class)
                .where(filters.toArray(new Filter[0]))
                .one();
    }

    /**
     * 查询同步进度（包含已同步数量、远端总数、同步日志摘要）
     */
    public Map<String, Object> getSyncProgress(String userId, String emailAccountId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 账号同步状态
        List<Filter> acctFilters = new ArrayList<>();
        acctFilters.add(Filter.eq("id", emailAccountId));
        acctFilters.add(Filter.eq("userId", userId));
        acctFilters.add(Filter.eq("delStatus", 0));
        Map<String, Object> account = MetaFactory.query(UserEmailAccount.class)
                .where(acctFilters.toArray(new Filter[0]))
                .one();

        if (account != null) {
            Object ss = account.get("syncStatus");
            Object syncStatus = (ss != null && Strings.isNotBlank(ss.toString())) ? ss.toString() : "idle";
            Object lastSyncAt = account.get("lastSyncAt");
            result.put("syncStatus", syncStatus);
            result.put("lastSyncAt", lastSyncAt);
        }

        // 2. 本地已同步邮件数
        long syncedCount = MetaFactory.query(EmailMessage.class)
                .where(
                        Filter.eq("emailAccountId", emailAccountId),
                        Filter.eq("delStatus", 0)
                )
                .count();
        result.put("syncedCount", syncedCount);

        // 3. 远端总数：取每个文件夹最新一次成功同步的 totalCount 求和
        long totalCount = 0;
        List<Map<String, Object>> latestLogs = getLatestSyncLogPerFolder(emailAccountId);
        List<Map<String, Object>> logSummaries = new ArrayList<>();
        for (Map<String, Object> log : latestLogs) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("folder", log.get("folder"));
            summary.put("status", log.get("status"));
            summary.put("syncType", log.get("syncType"));
            Object tc = log.get("totalCount");
            Object sc = log.get("syncedCount");
            int folderTotal = (tc instanceof Number n) ? n.intValue() : 0;
            int folderSynced = (sc instanceof Number n) ? n.intValue() : 0;
            summary.put("totalCount", folderTotal);
            summary.put("syncedCount", folderSynced);
            summary.put("startAt", log.get("startAt"));
            summary.put("endAt", log.get("endAt"));
            summary.put("errorMessage", log.get("errorMessage"));
            totalCount += folderTotal;
            logSummaries.add(summary);
        }
        result.put("totalCount", totalCount);
        result.put("folderLogs", logSummaries);

        return result;
    }

    /**
     * 获取每个文件夹最新一次同步日志
     */
    private List<Map<String, Object>> getLatestSyncLogPerFolder(String emailAccountId) {
        List<Map<String, Object>> allLogs = MetaFactory.query(EmailSyncLog.class)
                .where(
                        Filter.eq("emailAccountId", emailAccountId),
                        Filter.eq("delStatus", 0)
                )
                .order(Order.desc("startAt"))
                .page(1, 200)
                .list();

        // 按文件夹去重，保留最新一条
        Map<String, Map<String, Object>> latestByFolder = new HashMap<>();
        for (Map<String, Object> log : allLogs) {
            String folder = String.valueOf(log.get("folder"));
            if (!latestByFolder.containsKey(folder)) {
                latestByFolder.put(folder, log);
            }
        }
        return new ArrayList<>(latestByFolder.values());
    }

    /**
     * 开启/关闭同步
     */
    public void toggleSync(String userId, String emailAccountId, boolean enabled) {
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("id", emailAccountId));
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        Map<String, Object> row = MetaFactory.query(UserEmailAccount.class)
                .where(filters.toArray(new Filter[0]))
                .one();
        if (row == null || row.isEmpty()) {
            throw new IllegalArgumentException("邮箱账号不存在");
        }
        UserEmailAccount account = new UserEmailAccount();
        account.setId(emailAccountId);
        account.setSyncEnabled(enabled ? 1 : 0);
        if (enabled) {
            account.setSyncIntervalMinutes(defaultIntervalMinutes);
            account.setSyncStatus("idle");
        } else {
            account.setSyncStatus("idle");
        }
        MetaFactory.update(UserEmailAccount.class)
                .value("syncEnabled", account.getSyncEnabled())
                .value("syncIntervalMinutes", account.getSyncIntervalMinutes())
                .value("syncStatus", account.getSyncStatus())
                .where(Filter.eq("id", account.getId()))
                .save();
    }

    /**
     * 查询同步日志（最近N条）
     */
    public List<Map<String, Object>> getSyncLogs(String emailAccountId, int limit) {
        return MetaFactory.query(EmailSyncLog.class)
                .where(Filter.eq("emailAccountId", emailAccountId), Filter.eq("delStatus", 0))
                .order(Order.desc("startAt"))
                .page(1, Math.max(1, limit))
                .list();
    }

    // ------------------------------------------------------------------ 内部实现

    private void syncFolderTree(String userId, EmailAccountConfig config, Folder parentFolder, Store store) {
        try {
            Folder[] folders = parentFolder.list("%");
            if (folders == null) {
                return;
            }
            for (Folder folder : folders) {
                String fullName = folder.getFullName();
                if (Strings.isBlank(fullName)) {
                    continue;
                }
                int type;
                try {
                    type = folder.getType();
                } catch (Exception e) {
                    type = 0;
                }
                boolean holdsMessages = (type & Folder.HOLDS_MESSAGES) != 0;
                if (holdsMessages) {
                    try {
                        Folder openedFolder = store.getFolder(fullName);
                        doSyncFolder(userId, config, openedFolder, fullName);
                    } catch (Exception e) {
                        log.warn("同步文件夹 {} 失败, emailAccountId={}", fullName, config.id(), e);
                    }
                }
                // 递归处理子文件夹
                boolean holdsFolders = (type & Folder.HOLDS_FOLDERS) != 0;
                if (holdsFolders) {
                    syncFolderTree(userId, config, folder, store);
                }
            }
        } catch (Exception e) {
            log.warn("遍历文件夹树失败, emailAccountId={}", config.id(), e);
        }
    }

    /**
     * 同步单个文件夹（核心方法）
     */
    private EmailSyncLog doSyncFolder(String userId, EmailAccountConfig config, Folder imapFolder, String folderName) {
        EmailSyncLog syncLog = createSyncLog(config.id(), folderName);
        int syncedCount = 0;
        long maxUid = 0;

        try {
            imapFolder.open(Folder.READ_ONLY);
            long uidValidity = EmailInboxService.resolveUidValidity(imapFolder);

            // 远端总数在 open 后就可以获取，提前设置确保 INSERT 时写入
            syncLog.setTotalCount(imapFolder.getMessageCount());

            // 查询上次同步的最大 UID
            Long lastUid = findLastSyncedUid(config.id(), folderName, uidValidity);
            String syncType = (lastUid != null && lastUid > 0) ? "incremental" : "full";
            syncLog.setSyncType(syncType);
            syncLog.setStatus("running");
            syncLog.setStartAt(new Date());
            saveSyncLog(syncLog);

            Message[] messages;
            if (imapFolder instanceof UIDFolder uidFolder) {
                long startUid = (lastUid != null) ? lastUid + 1 : 1;
                messages = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID);
                if (messages == null) {
                    messages = new Message[0];
                }
            } else {
                // 非 UIDFolder 降级处理：取全量
                int mc = imapFolder.getMessageCount();
                if (mc <= 0) {
                    messages = new Message[0];
                } else {
                    messages = imapFolder.getMessages(1, mc);
                }
            }

            log.info("同步文件夹 {}, emailAccountId={}, type={}, messageCount={}",
                    folderName, config.id(), syncType, messages.length);

            // 批量处理邮件
            List<Message> batch = new ArrayList<>(batchSize);
            for (Message msg : messages) {
                batch.add(msg);
                if (batch.size() >= batchSize) {
                    int count = processBatch(userId, config.id(), folderName, uidValidity, imapFolder, batch);
                    syncedCount += count;
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                int count = processBatch(userId, config.id(), folderName, uidValidity, imapFolder, batch);
                syncedCount += count;
            }

            // 记录最大 UID
            if (messages.length > 0 && imapFolder instanceof UIDFolder uidFolder) {
                Message lastMsg = messages[messages.length - 1];
                maxUid = uidFolder.getUID(lastMsg);
            }

            syncLog.setStatus("success");
            syncLog.setSyncedCount(syncedCount);
            syncLog.setLastUid(maxUid);
            syncLog.setEndAt(new Date());
            updateSyncLog(syncLog);

        } catch (Exception e) {
            log.error("同步文件夹 {} 失败, emailAccountId={}", folderName, config.id(), e);
            syncLog.setStatus("error");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setSyncedCount(syncedCount);
            syncLog.setEndAt(new Date());
            updateSyncLog(syncLog);
        } finally {
            EmailInboxService.closeQuietly(imapFolder);
        }
        return syncLog;
    }

    /**
     * 批量处理一组邮件消息：解析+入库+附件上传OSS
     */
    private int processBatch(String userId, String emailAccountId, String folderName,
                             long uidValidity, Folder imapFolder, List<Message> batch) {
        int count = 0;
        for (Message msg : batch) {
            try {
                processOneMessage(userId, emailAccountId, folderName, uidValidity, imapFolder, msg);
                count++;
            } catch (Exception e) {
                log.warn("处理邮件失败, folder={}, emailAccountId={}", folderName, emailAccountId, e);
            }
        }
        return count;
    }

    /**
     * 处理单封邮件：解析元数据+正文存 EmailMessage，附件下载后上传 OSS 存 EmailAttachment
     */
    private void processOneMessage(String userId, String emailAccountId, String folderName,
                                   long uidValidity, Folder imapFolder, Message msg) throws Exception {
        long uid = EmailInboxService.resolveUid(imapFolder, msg);

        // 检查是否已存在
        boolean exists = MetaFactory.query(EmailMessage.class)
                .where(
                        Filter.eq("emailAccountId", emailAccountId),
                        Filter.eq("folder", folderName),
                        Filter.eq("uidValidity", uidValidity),
                        Filter.eq("uid", uid),
                        Filter.eq("delStatus", 0)
                )
                .exists();
        if (exists) {
            return;
        }

        // 解析邮件内容
        EmailInboxService.MessageContent content = new EmailInboxService.MessageContent();
        EmailInboxService.parsePart(msg, content);

        // 构建 EmailMessage 并保存
        String messageId = String.valueOf(UIDGenerator.generate());
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setId(messageId);
        emailMessage.setEmailAccountId(emailAccountId);
        emailMessage.setUserId(userId);
        emailMessage.setFolder(folderName);
        emailMessage.setUid(uid);
        emailMessage.setUidValidity(uidValidity);
        emailMessage.setMessageId(EmailInboxService.extractMessageId(msg));
        emailMessage.setSubject(msg.getSubject());
        emailMessage.setFromJson(toJson(EmailInboxService.firstAddress(msg.getFrom())));
        emailMessage.setToJson(toJson(EmailInboxService.addressList(msg.getRecipients(Message.RecipientType.TO))));
        emailMessage.setCcJson(toJson(EmailInboxService.addressList(msg.getRecipients(Message.RecipientType.CC))));
        emailMessage.setBccJson(toJson(EmailInboxService.addressList(msg.getRecipients(Message.RecipientType.BCC))));
        emailMessage.setSentAt(EmailInboxService.extractSentDate(msg));
        emailMessage.setReceivedAt(EmailInboxService.extractReceivedDate(msg));
        emailMessage.setSize(EmailInboxService.extractSize(msg));
        emailMessage.setUnread(msg.isSet(Flags.Flag.SEEN) ? 0 : 1);
        emailMessage.setHasAttachments(!content.getAttachments().isEmpty() ? 1 : 0);
        emailMessage.setTextBody(content.getTextBody());
        emailMessage.setHtmlBody(content.getHtmlBody());
        emailMessage.setSnippet(buildSnippet(content.getTextBody()));
        emailMessage.setSyncedAt(new Date());

        MetaFactory.insert(EmailMessage.class)
                .value("id", emailMessage.getId())
                .value("emailAccountId", emailMessage.getEmailAccountId())
                .value("userId", emailMessage.getUserId())
                .value("folder", emailMessage.getFolder())
                .value("uid", emailMessage.getUid())
                .value("uidValidity", emailMessage.getUidValidity())
                .value("messageId", emailMessage.getMessageId())
                .value("subject", emailMessage.getSubject())
                .value("fromJson", emailMessage.getFromJson())
                .value("toJson", emailMessage.getToJson())
                .value("ccJson", emailMessage.getCcJson())
                .value("bccJson", emailMessage.getBccJson())
                .value("sentAt", emailMessage.getSentAt())
                .value("receivedAt", emailMessage.getReceivedAt())
                .value("size", emailMessage.getSize())
                .value("unread", emailMessage.getUnread())
                .value("hasAttachments", emailMessage.getHasAttachments())
                .value("textBody", emailMessage.getTextBody())
                .value("htmlBody", emailMessage.getHtmlBody())
                .value("snippet", emailMessage.getSnippet())
                .value("syncedAt", emailMessage.getSyncedAt())
                .save();

        // 处理附件
        List<Part> attachmentParts = content.getAttachments();
        for (int i = 0; i < attachmentParts.size(); i++) {
            Part part = attachmentParts.get(i);
            String partId = String.valueOf(i + 1);
            String fileName = EmailInboxService.resolveFileName(part);
            String contentType = EmailInboxService.safeContentType(part.getContentType());
            boolean isInline = Part.INLINE.equalsIgnoreCase(part.getDisposition());
            String contentId = EmailInboxService.extractHeader(part, "Content-ID");

            EmailAttachment emailAttachment = new EmailAttachment();
            emailAttachment.setId(String.valueOf(UIDGenerator.generate()));
            emailAttachment.setEmailMessageId(messageId);
            emailAttachment.setPartId(partId);
            emailAttachment.setFileName(fileName);
            emailAttachment.setContentType(contentType);
            emailAttachment.setInline(isInline ? 1 : 0);
            emailAttachment.setContentId(contentId);

            // 下载附件到临时文件并上传 OSS
            try (InputStream partStream = part.getInputStream()) {
                String ext = FileUtils.getFileExtension(Strings.isNotBlank(fileName) ? fileName : "attachment");
                File tempFile = FileUtils.createTempFile(partStream, ext);
                try {
                    FileParam fileParam = new FileParam();
                    fileParam.setServiceType("ALIYUN");
                    fileParam.setSourceType("email_attachment");
                    fileParam.setObjectId(messageId);
                    Attachment attachment = fileHandler.upload(tempFile, Strings.isNotBlank(fileName) ? fileName : "attachment-" + partId, fileParam);
                    if (attachment != null) {
                        emailAttachment.setAttachmentId(attachment.getId());
                        emailAttachment.setSize(attachment.getSize());
                    }
                } finally {
                    java.nio.file.Files.deleteIfExists(tempFile.toPath());
                }
            } catch (Exception e) {
                log.warn("上传附件失败, messageId={}, partId={}, fileName={}", messageId, partId, fileName, e);
            }

            MetaFactory.insert(EmailAttachment.class)
                    .value("id", emailAttachment.getId())
                    .value("emailMessageId", emailAttachment.getEmailMessageId())
                    .value("partId", emailAttachment.getPartId())
                    .value("fileName", emailAttachment.getFileName())
                    .value("contentType", emailAttachment.getContentType())
                    .value("inline", emailAttachment.getInline())
                    .value("contentId", emailAttachment.getContentId())
                    .value("attachmentId", emailAttachment.getAttachmentId())
                    .value("size", emailAttachment.getSize())
                    .save();
        }
    }

    // ------------------------------------------------------------------ 辅助方法

    private Long findLastSyncedUid(String emailAccountId, String folderName, long uidValidity) {
        try {
            Map<String, Object> row = MetaFactory.query(EmailSyncLog.class)
                    .where(
                            Filter.eq("emailAccountId", emailAccountId),
                            Filter.eq("folder", folderName),
                            Filter.eq("status", "success"),
                            Filter.eq("delStatus", 0)
                    )
                    .order(Order.desc("startAt"))
                    .one();
            if (row != null) {
                Object lastUid = row.get("lastUid");
                if (lastUid instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Exception e) {
            log.debug("查询上次同步UID失败, emailAccountId={}, folder={}", emailAccountId, folderName, e);
        }
        return null;
    }

    private EmailSyncLog createSyncLog(String emailAccountId, String folderName) {
        EmailSyncLog syncLog = new EmailSyncLog();
        syncLog.setId(String.valueOf(UIDGenerator.generate()));
        syncLog.setEmailAccountId(emailAccountId);
        syncLog.setFolder(folderName);
        syncLog.setStartAt(new Date());
        return syncLog;
    }

    private void saveSyncLog(EmailSyncLog syncLog) {
        try {
            MetaFactory.insert(EmailSyncLog.class)
                    .value("id", syncLog.getId())
                    .value("emailAccountId", syncLog.getEmailAccountId())
                    .value("folder", syncLog.getFolder())
                    .value("syncType", syncLog.getSyncType())
                    .value("status", syncLog.getStatus())
                    .value("lastUid", syncLog.getLastUid())
                    .value("totalCount", syncLog.getTotalCount())
                    .value("syncedCount", syncLog.getSyncedCount())
                    .value("errorMessage", syncLog.getErrorMessage())
                    .value("startAt", syncLog.getStartAt())
                    .value("endAt", syncLog.getEndAt())
                    .save();
        } catch (Exception e) {
            log.warn("保存同步日志失败", e);
        }
    }

    private void updateSyncLog(EmailSyncLog syncLog) {
        try {
            MetaFactory.update(EmailSyncLog.class)
                    .value("status", syncLog.getStatus())
                    .value("lastUid", syncLog.getLastUid())
                    .value("totalCount", syncLog.getTotalCount())
                    .value("syncedCount", syncLog.getSyncedCount())
                    .value("errorMessage", syncLog.getErrorMessage())
                    .value("endAt", syncLog.getEndAt())
                    .where(Filter.eq("id", syncLog.getId()))
                    .save();
        } catch (Exception e) {
            log.warn("更新同步日志失败, id={}, folder={}", syncLog.getId(), syncLog.getFolder(), e);
        }
    }

    private void updateAccountSyncStatus(String emailAccountId, String status) {
        try {
            MetaFactory.update(UserEmailAccount.class)
                    .value("syncStatus", status)
                    .where(Filter.eq("id", emailAccountId))
                    .save();
        } catch (Exception e) {
            log.warn("更新账号同步状态失败, emailAccountId={}, status={}", emailAccountId, status, e);
        }
    }

    private void updateAccountLastSyncAt(String emailAccountId) {
        try {
            MetaFactory.update(UserEmailAccount.class)
                    .value("lastSyncAt", new Date())
                    .where(Filter.eq("id", emailAccountId))
                    .save();
        } catch (Exception e) {
            log.warn("更新账号最后同步时间失败, emailAccountId={}", emailAccountId, e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSnippet(String textBody) {
        if (Strings.isBlank(textBody)) {
            return null;
        }
        String trimmed = textBody.replaceAll("\\s+", " ").trim();
        if (trimmed.length() > 500) {
            return trimmed.substring(0, 500);
        }
        return trimmed;
    }
}
