package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailFolderCustom;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.util.MailSessionCtx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮箱账户服务：CRUD + 凭据加解密 + 归属校验。
 *
 * 数据隔离：所有查询按当前登录用户 userId + tenantCode 过滤；
 * 响应永不返回 passwordCipher（见 {@link #toResponse}）。
 */
@Service
public class MailAccountService {

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailCryptoService cryptoService;

    /**
     * 查询当前用户的全部邮箱账户（按创建时间升序、id 升序兜底）。
     *
     * <p>显式排序保证首行确定性：前端 currentAccount getter 的兜底链
     * （currentAccountId → isDefault → 列表首行）依赖首行稳定，
     * 无 ORDER BY 时列表首行不确定会导致多账户环境兜底账号漂移（ST-23-B8）。
     */
    public List<MailAccount> listByCurrentUser() {
        MetaQuery query = MetaFactory.query(MailAccount.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("tenantCode", MailSessionCtx.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("createAt"), Order.asc("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity).collect(Collectors.toList());
    }

    /** 查询账户并校验归属当前用户（越权/不存在返回 null） */
    public MailAccount getOwned(String accountId) {
        MetaQuery query = MetaFactory.query(MailAccount.class)
                .where(Filter.eq("id", accountId),
                        Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(rows.get(0));
    }

    /** 创建账户（密码明文在此加密落库；首个账户自动设为默认） */
    public MailAccount create(MailAccount account, String plainPassword) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        String tenantCode = MailSessionCtx.getCurrentTenantCode();
        Date now = new Date();

        account.setUserId(userId);
        account.setPasswordCipher(cryptoService.encrypt(plainPassword));
        if (listByCurrentUser().isEmpty()) {
            account.setIsDefault(1);
        }
        account.setTenantCode(tenantCode);
        account.setDelStatus(0);
        account.setCreateAt(now);
        account.setUpdateAt(now);
        account.setCreator(userId);
        account.setCreatorName(userName);
        account.setUpdater(userId);
        account.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(account);
        if (account.getId() == null && saved != null && saved.get("id") != null) {
            account.setId(String.valueOf(saved.get("id")));
        }
        return account;
    }

    /**
     * 局部更新账户（仅更新请求中出现的字段；凭据更新时 AES-GCM 重新加密）。
     *
     * <p>isDefault=true 时清除当前用户其他账户的默认标志（保证唯一默认）；
     * 显式 isDefault=false 允许「无默认账户」（resolveAccount 有唯一账户回退）。
     *
     * <p>email 变更且未显式给 username 时，username 随 email 同步（对齐 create 逻辑）。
     *
     * <p>不强制连通性验证：编辑常用于修复已失效配置（服务器迁移/密码轮换），
     * 强制 verify 会把用户锁死在「旧配置已失效、新配置暂不可达」状态；
     * 前端可经 POST /accounts/verify 显式前置验证（与创建流程同端点）。
     */
    public MailAccount update(MailAccount account, AccountUpdateRequest req) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();

        if (req.getName() != null) {
            account.setName(req.getName().trim());
        }
        if (req.getEmail() != null) {
            account.setEmail(req.getEmail().trim());
            if (req.getUsername() == null) {
                account.setUsername(req.getEmail().trim());
            }
        }
        if (req.getAvatar() != null) {
            account.setAvatar(req.getAvatar().trim());
        }
        if (req.getSignature() != null) {
            account.setSignature(req.getSignature());
        }
        if (req.getProviderCode() != null) {
            account.setProviderCode(req.getProviderCode().trim());
        }
        if (req.getUsername() != null) {
            account.setUsername(req.getUsername().trim());
        }
        if (req.getPassword() != null) {
            account.setPasswordCipher(cryptoService.encrypt(req.getPassword()));
        }
        if (req.getServers() != null) {
            AccountUpdateRequest.Incoming in = req.getServers().getIncoming();
            if (in != null) {
                account.setIncomingHost(in.getHost().trim());
                account.setIncomingPort(in.getPort());
                if (in.getProtocol() != null) {
                    account.setIncomingProtocol(in.getProtocol().trim());
                }
                if (in.getEncryption() != null) {
                    account.setIncomingEncryption(in.getEncryption().trim());
                }
            }
            AccountUpdateRequest.Outgoing out = req.getServers().getOutgoing();
            if (out != null) {
                account.setOutgoingHost(out.getHost().trim());
                account.setOutgoingPort(out.getPort());
                if (out.getEncryption() != null) {
                    account.setOutgoingEncryption(out.getEncryption().trim());
                }
            }
        }
        if (req.getIsDefault() != null) {
            if (req.getIsDefault()) {
                clearOtherDefaults(account.getId(), userId, userName, now);
                account.setIsDefault(1);
            } else {
                account.setIsDefault(0);
            }
        }
        if (req.getSyncEnabled() != null) {
            account.setSyncEnabled(req.getSyncEnabled() ? 1 : 0);
        }
        if (req.getSyncIntervalMinutes() != null) {
            account.setSyncIntervalMinutes(req.getSyncIntervalMinutes());
        }
        account.setUpdateAt(now);
        account.setUpdater(userId);
        account.setUpdaterName(userName);
        dynamicDao.save(account);
        return account;
    }

    /**
     * 逻辑删除账户，并级联逻辑删除其邮件与自定义文件夹。
     *
     * <p>级联论证：mail_message.account_id 非空无法重挂；账户删除后保留邮件会在
     * 「全部账户」列表/计数（list/folderCounts 按 userId 聚合、不带 accountId 时）成为
     * 可见孤儿数据。逻辑删（del_status>0）可恢复，与标签/过滤器/自定义文件夹删除同口径。
     *
     * <p>不级联：标签/签名/联系人按用户级隔离（listEntities 仅按 userId 过滤，accountId
     * 可空表示用户级共享），账户删除不影响其可见性。
     *
     * <p>删除默认账户且仍有剩余账户时，最早创建的剩余账户自动接任默认。
     *
     * <p><b>del_status 语义（B4 修复）</b>：uk_user_email(user_id, email, del_status)
     * 唯一索引下，若删除恒写常量 1，同 (user_id, email) 第二次删除会与首行幽灵记录
     * 撞 UK（1062→500）。修复后 del_status 由行 id 派生（唯一删除标记），取值
     * 「0=未删，&gt;0=删除标识」。查询面全部 {@code Filter.eq("delStatus", 0)} 语义
     * 不受影响。零迁移：列保持 INT，id 派生值经 {@code & 0x7FFFFFFF} 落入正整数域。
     *
     * @return 级联统计 {cascadeMessages, cascadeFolders}
     */
    public Map<String, Integer> delete(MailAccount account) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();

        int cascadeMessages = logicalDeleteMessages(account.getId(), userId, userName, now);
        int cascadeFolders = logicalDeleteCustomFolders(account.getId(), userId, userName, now);

        account.setDelStatus(deletionMarkerOf(account.getId()));
        account.setDeleteAt(now);
        account.setUpdateAt(now);
        account.setUpdater(userId);
        account.setUpdaterName(userName);
        dynamicDao.save(account);

        if (account.getIsDefault() == 1) {
            promoteEarliestAsDefault(account.getId(), userId, userName, now);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("cascadeMessages", cascadeMessages);
        result.put("cascadeFolders", cascadeFolders);
        return result;
    }

    /**
     * B4 删除标记：行 id 派生的正整数（&lt;= 2^31-1），用于 del_status 列。
     *
     * <p>同一 (user_id, email) 维度下，不同行 id 派生值高概率唯一，避免 UK 幽灵行
     * 冲突。雪花 id（19 位数字串）取低 31 位；非数字 id（测试桩/异常路径）退化为
     * {@code String.hashCode() & 0x7FFFFFFF}。空 id 兜底为当前毫秒低 31 位（理论
     * 撞同毫秒的极端场景仍存在，但账户删除无并发批量路径，可忽略）。
     */
    private static int deletionMarkerOf(String id) {
        int marker;
        if (id == null || id.isEmpty()) {
            marker = (int) (System.currentTimeMillis() & 0x7FFFFFFFL);
        } else {
            try {
                marker = (int) (Long.parseLong(id) & 0x7FFFFFFFL);
            } catch (NumberFormatException e) {
                marker = id.hashCode() & 0x7FFFFFFF;
            }
        }
        // 0 保留给"未删"语义：派生值低 31 位恰为 0 时（概率 1/2^31）退化为 1，保证 marker>0 契约
        return marker == 0 ? 1 : marker;
    }

    /** 解密账户密码（KEK 未配置 fail-fast） */
    public String decryptPassword(MailAccount account) {
        return cryptoService.decrypt(account.getPasswordCipher());
    }

    /** 更新同步状态（sync 完成后回写） */
    public void markSyncResult(String accountId, boolean success) {
        MailAccount account = getOwned(accountId);
        if (account == null) {
            return;
        }
        account.setLastSyncAt(new Date());
        account.setLastSyncStatus(success ? "success" : "failed");
        account.setUpdateAt(new Date());
        account.setUpdater(MailSessionCtx.getCurrentUserId());
        account.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(account);
    }

    /** 标记账户正在同步（定时任务防重叠标记；完成/失败后由 markSyncResult 覆盖） */
    public void markSyncRunning(String accountId) {
        MailAccount account = getOwned(accountId);
        if (account == null) {
            return;
        }
        account.setLastSyncStatus("syncing");
        account.setUpdateAt(new Date());
        account.setUpdater(MailSessionCtx.getCurrentUserId());
        account.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(account);
    }

    /** 转前端 MailAccount 契约（不含凭据密文） */
    public Map<String, Object> toResponse(MailAccount account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("name", account.getName());
        map.put("email", account.getEmail());
        map.put("avatar", account.getAvatar());
        map.put("signature", account.getSignature());
        map.put("isDefault", account.getIsDefault() == 1);
        map.put("providerCode", account.getProviderCode());
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("protocol", account.getIncomingProtocol());
        incoming.put("host", account.getIncomingHost());
        incoming.put("port", account.getIncomingPort());
        incoming.put("encryption", account.getIncomingEncryption());
        Map<String, Object> outgoing = new LinkedHashMap<>();
        outgoing.put("host", account.getOutgoingHost());
        outgoing.put("port", account.getOutgoingPort());
        outgoing.put("encryption", account.getOutgoingEncryption());
        Map<String, Object> servers = new LinkedHashMap<>();
        servers.put("incoming", incoming);
        servers.put("outgoing", outgoing);
        map.put("servers", servers);
        map.put("syncEnabled", account.getSyncEnabled() == 1);
        map.put("syncIntervalMinutes", account.getSyncIntervalMinutes());
        map.put("lastSyncAt", account.getLastSyncAt());
        map.put("lastSyncStatus", account.getLastSyncStatus());
        return map;
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    @SuppressWarnings("unchecked")
    private MailAccount toEntity(Map<String, Object> row) {
        MailAccount account = new MailAccount();
        account.setId(str(row.get("id")));
        account.setUserId(str(row.get("userId")));
        account.setName(str(row.get("name")));
        account.setEmail(str(row.get("email")));
        account.setAvatar(str(row.get("avatar")));
        account.setSignature(str(row.get("signature")));
        account.setIsDefault(intVal(row.get("isDefault")));
        account.setProviderCode(str(row.get("providerCode")));
        account.setIncomingProtocol(str(row.get("incomingProtocol")));
        account.setIncomingHost(str(row.get("incomingHost")));
        account.setIncomingPort(intVal(row.get("incomingPort")));
        account.setIncomingEncryption(str(row.get("incomingEncryption")));
        account.setOutgoingHost(str(row.get("outgoingHost")));
        account.setOutgoingPort(intVal(row.get("outgoingPort")));
        account.setOutgoingEncryption(str(row.get("outgoingEncryption")));
        account.setUsername(str(row.get("username")));
        account.setPasswordCipher(str(row.get("passwordCipher")));
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值，
        // 且 getOwned→变更→save 的读改写链路会把 null 写回数据库，曾致 createAt 等列被清空）
        account.setLastSyncAt(MailMessageService.toDate(row.get("lastSyncAt")));
        account.setLastSyncStatus(str(row.get("lastSyncStatus")));
        account.setSyncEnabled(intVal(row.get("syncEnabled")));
        account.setSyncIntervalMinutes(intObj(row.get("syncIntervalMinutes")));
        account.setTenantCode(str(row.get("tenantCode")));
        // 基类全列字段必须完整映射：Dao 全列 UPDATE 会把未映射列写为 null/0 造成静默丢值
        // （R1 实证：promote stale 行回写清掉 delete_at；seqNo/buId/deptId 同理）
        account.setDelStatus(intVal(row.get("delStatus")));
        account.setDeleteAt(MailMessageService.toDate(row.get("deleteAt")));
        account.setSeqNo(longVal(row.get("seqNo")));
        account.setBuId(str(row.get("buId")));
        account.setDeptId(str(row.get("deptId")));
        account.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        account.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        account.setCreator(str(row.get("creator")));
        account.setCreatorName(str(row.get("creatorName")));
        account.setUpdater(str(row.get("updater")));
        account.setUpdaterName(str(row.get("updaterName")));
        return account;
    }

    // ==================== 内部辅助 ====================

    /** 清除当前用户其他账户的默认标志（设默认前调用，保证唯一默认）。包可见以便单测 spy 隔离 MetaQuery 静态查询 */
    void clearOtherDefaults(String exceptId, String userId, String userName, Date now) {
        MetaQuery query = MetaFactory.query(MailAccount.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("isDefault", 1),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        for (Map<String, Object> row : rows) {
            MailAccount other = toEntity(row);
            if (other.getId() == null || other.getId().equals(exceptId)) {
                continue;
            }
            other.setIsDefault(0);
            other.setUpdateAt(now);
            other.setUpdater(userId);
            other.setUpdaterName(userName);
            dynamicDao.save(other);
        }
    }

    /** 级联逻辑删除账户全部邮件（与自定义文件夹删除的邮件迁移同模式：逐条 save 写审计字段）。包可见以便单测 spy 隔离 */
    int logicalDeleteMessages(String accountId, String userId, String userName, Date now) {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("accountId", accountId),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        int count = 0;
        for (Map<String, Object> row : rows) {
            Object mailId = row.get("id");
            if (mailId == null) {
                continue;
            }
            MailMessage msg = dynamicDao.queryForObject(MailMessage.class, String.valueOf(mailId));
            if (msg == null) {
                continue;
            }
            msg.setDelStatus(1);
            msg.setDeleteAt(now);
            msg.setUpdateAt(now);
            msg.setUpdater(userId);
            msg.setUpdaterName(userName);
            dynamicDao.save(msg);
            count++;
        }
        return count;
    }

    /** 级联逻辑删除账户全部自定义文件夹。包可见以便单测 spy 隔离 */
    int logicalDeleteCustomFolders(String accountId, String userId, String userName, Date now) {
        MetaQuery query = MetaFactory.query(MailFolderCustom.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("accountId", accountId),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        int count = 0;
        for (Map<String, Object> row : rows) {
            Object folderId = row.get("id");
            if (folderId == null) {
                continue;
            }
            MailFolderCustom folder = dynamicDao.queryForObject(MailFolderCustom.class, String.valueOf(folderId));
            if (folder == null) {
                continue;
            }
            folder.setDelStatus(1);
            folder.setDeleteAt(now);
            folder.setUpdateAt(now);
            folder.setUpdater(userId);
            folder.setUpdaterName(userName);
            dynamicDao.save(folder);
            count++;
        }
        return count;
    }

    /**
     * 最早创建的剩余账户接任默认（删除默认账户后调用；无剩余账户则不动）。包可见以便单测 spy 隔离。
     *
     * <p>必须显式按 id 排除刚删除的账户：deleteAccount 处于 @Transactional 中，平台 ORM
     * 本方法内 MetaQuery 读看不到同事务动态 Dao 未提交的 del_status=1 写入（读写路径分离），
     * 仅靠 delStatus=0 过滤会把已删账户自身选为继任者，其全列回写（stale 行 del_status=0、
     * delete_at=null）会在提交时覆盖删除写入，导致删除静默失效（R1 冒烟 D3/D4 实证）。
     */
    void promoteEarliestAsDefault(String excludeId, String userId, String userName, Date now) {
        MetaQuery query = MetaFactory.query(MailAccount.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0),
                        Filter.ne("id", excludeId))
                .order(Order.asc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        MailAccount successor = toEntity(rows.get(0));
        successor.setIsDefault(1);
        successor.setUpdateAt(now);
        successor.setUpdater(userId);
        successor.setUpdaterName(userName);
        dynamicDao.save(successor);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private int intVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private Integer intObj(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long longVal(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }

    // ==================== DTO ====================

    /**
     * 账户局部更新请求（PATCH/PUT /accounts/{id}）。
     * 全部字段可选；出现即更新（null = 不动）。servers.incoming/outgoing 出现即按方向
     * 整体替换 host/port（protocol/encryption 缺省保留现值）。
     */
    @lombok.Data
    public static class AccountUpdateRequest {
        private static final Set<String> PROTOCOLS = Set.of("imap", "pop3");
        private static final Set<String> ENCRYPTIONS = Set.of("ssl", "tls", "none");

        private String name;
        private String email;
        private String avatar;
        private String signature;
        private String providerCode;
        private String username;
        private String password;
        private Boolean isDefault;
        private Boolean syncEnabled;
        private Integer syncIntervalMinutes;
        private Servers servers;

        /** 无任何字段出现（空 PATCH 体拒绝，避免误调） */
        public boolean isEmpty() {
            return name == null && email == null && avatar == null && signature == null
                    && providerCode == null && username == null && password == null
                    && isDefault == null && syncEnabled == null && syncIntervalMinutes == null
                    && servers == null;
        }

        /** 取值校验；返回错误信息，null 表示通过 */
        public String validate() {
            if (name != null && name.isBlank()) {
                return "账户显示名不能为空白";
            }
            if (email != null && email.isBlank()) {
                return "邮箱地址不能为空白";
            }
            if (username != null && username.isBlank()) {
                return "登录用户名不能为空白";
            }
            if (password != null && password.isBlank()) {
                return "邮箱密码/授权码不能为空白";
            }
            if (syncIntervalMinutes != null && (syncIntervalMinutes < 1 || syncIntervalMinutes > 1440)) {
                return "定时同步间隔非法（1-1440 分钟）";
            }
            if (servers != null) {
                Incoming in = servers.getIncoming();
                if (in != null) {
                    String invalid = validateServer(in.getHost(), in.getPort(), in.getProtocol(),
                            in.getEncryption(), "收信");
                    if (invalid != null) {
                        return invalid;
                    }
                }
                Outgoing out = servers.getOutgoing();
                if (out != null) {
                    String invalid = validateServer(out.getHost(), out.getPort(), null,
                            out.getEncryption(), "发信");
                    if (invalid != null) {
                        return invalid;
                    }
                }
            }
            return null;
        }

        private String validateServer(String host, int port, String protocol, String encryption, String label) {
            if (host == null || host.isBlank()) {
                return label + "服务器主机不能为空";
            }
            if (port <= 0 || port > 65535) {
                return label + "服务器端口非法（1-65535）";
            }
            if (protocol != null && !PROTOCOLS.contains(protocol.trim())) {
                return label + "协议仅支持 imap/pop3";
            }
            if (encryption != null && !ENCRYPTIONS.contains(encryption.trim())) {
                return label + "加密仅支持 ssl/tls/none";
            }
            return null;
        }

        @lombok.Data
        public static class Servers {
            private Incoming incoming;
            private Outgoing outgoing;
        }

        @lombok.Data
        public static class Incoming {
            private String protocol;
            private String host;
            private int port;
            private String encryption;
        }

        @lombok.Data
        public static class Outgoing {
            private String host;
            private int port;
            private String encryption;
        }
    }
}
