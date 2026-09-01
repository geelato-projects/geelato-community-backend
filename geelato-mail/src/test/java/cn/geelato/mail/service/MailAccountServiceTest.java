package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.service.MailAccountService.AccountUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MailAccountService 单元测试。
 *
 * 覆盖场景：
 * - create：dynamicDao.save 返回的雪花 id 回填实体（修复前响应 id=null）；
 *   同测首个账户 isDefault=1 + 凭据加密落库 + 审计字段
 * - create：save 返回缺 id 时不回填（保持 null，不捏造）；同测不抢默认
 * - update：凭据更新经 AES-GCM 重新加密；email 变更随动 username（显式 username 优先）；
 *   servers 按方向整体替换 host/port、protocol/encryption 缺省保留；isDefault=false 允许；
 *   isDefault=true 先清其他默认
 * - update DTO 校验：空白/端口越界/非法协议加密/空体 isEmpty
 * - delete：级联逻辑删除（邮件/自定义文件夹计数透传）+ 审计字段 + 默认账户接任触发
 */
class MailAccountServiceTest {

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";

    private MailAccountService service;
    private Dao dynamicDao;
    private MailCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        dynamicDao = mock(Dao.class);
        cryptoService = mock(MailCryptoService.class);
        service = spy(new MailAccountService());
        ReflectionTestUtils.setField(service, "dynamicDao", dynamicDao);
        ReflectionTestUtils.setField(service, "cryptoService", cryptoService);
        User user = new User();
        user.setUserId(USER_ID);
        user.setUserName(USER_NAME);
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant(TENANT_CODE));
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("create：dynamicDao.save 返回的雪花 id 回填实体（修复 L-1 响应 id:null）")
    void test_create_backfillsGeneratedId() {
        when(cryptoService.encrypt("plain-secret")).thenReturn("cipher-text");
        doReturn(List.of()).when(service).listByCurrentUser();
        when(dynamicDao.save(any(MailAccount.class))).thenReturn(Map.of("id", "snowflake-001"));

        MailAccount account = new MailAccount();
        account.setName("企业邮");
        account.setEmail("zhangsan@example.com");
        MailAccount created = service.create(account, "plain-secret");

        assertEquals("snowflake-001", created.getId(), "save 生成的雪花 id 必须回填实体并随响应返回");
        assertEquals(1, created.getIsDefault(), "首个账户应自动设为默认");
        ArgumentCaptor<MailAccount> captor = ArgumentCaptor.forClass(MailAccount.class);
        verify(dynamicDao).save(captor.capture());
        MailAccount saved = captor.getValue();
        assertEquals("cipher-text", saved.getPasswordCipher());
        assertEquals(USER_ID, saved.getCreator());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(TENANT_CODE, saved.getTenantCode());
    }

    @Test
    @DisplayName("create：save 返回缺 id 时不捏造回填（保持 null）")
    void test_create_noFabricationWhenIdMissing() {
        when(cryptoService.encrypt(any())).thenReturn("cipher-text");
        doReturn(List.of(new MailAccount())).when(service).listByCurrentUser();
        when(dynamicDao.save(any(MailAccount.class))).thenReturn(Map.of());

        MailAccount created = service.create(new MailAccount(), "plain-secret");

        assertNull(created.getId(), "save 未返回 id 时不得捏造");
        assertEquals(0, created.getIsDefault(), "已有账户时新账户不抢默认");
    }

    // ==================== update ====================

    private MailAccount ownedAccount() {
        MailAccount account = new MailAccount();
        account.setId("acc-001");
        account.setUserId(USER_ID);
        account.setName("企业邮");
        account.setEmail("zhangsan@example.com");
        account.setUsername("zhangsan@example.com");
        account.setPasswordCipher("old-cipher");
        account.setProviderCode("custom");
        account.setIncomingProtocol("imap");
        account.setIncomingHost("imap.example.com");
        account.setIncomingPort(993);
        account.setIncomingEncryption("ssl");
        account.setOutgoingHost("smtp.example.com");
        account.setOutgoingPort(465);
        account.setOutgoingEncryption("ssl");
        account.setIsDefault(0);
        return account;
    }

    @Test
    @DisplayName("update：凭据更新经 AES-GCM 重新加密，旧密文被替换")
    void test_update_reEncryptsPassword() {
        when(cryptoService.encrypt("new-secret")).thenReturn("new-cipher");
        MailAccount account = ownedAccount();
        AccountUpdateRequest req = new AccountUpdateRequest();
        req.setPassword("new-secret");

        MailAccount updated = service.update(account, req);

        assertEquals("new-cipher", updated.getPasswordCipher(), "凭据更新必须重新加密落库");
        verify(cryptoService).encrypt("new-secret");
        ArgumentCaptor<MailAccount> captor = ArgumentCaptor.forClass(MailAccount.class);
        verify(dynamicDao).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUpdater());
        assertNotNull(captor.getValue().getUpdateAt());
        // 未出现字段不动
        assertEquals("企业邮", updated.getName());
        assertEquals("imap.example.com", updated.getIncomingHost());
    }

    @Test
    @DisplayName("update：email 变更且未显式给 username 时 username 随 email 同步")
    void test_update_emailSyncsUsername() {
        MailAccount account = ownedAccount();
        AccountUpdateRequest req = new AccountUpdateRequest();
        req.setEmail("new@example.com");

        MailAccount updated = service.update(account, req);

        assertEquals("new@example.com", updated.getEmail());
        assertEquals("new@example.com", updated.getUsername(), "对齐 create 逻辑：username 默认同邮箱");
    }

    @Test
    @DisplayName("update：显式 username 优先于 email 同步")
    void test_update_explicitUsernameWins() {
        MailAccount account = ownedAccount();
        AccountUpdateRequest req = new AccountUpdateRequest();
        req.setEmail("new@example.com");
        req.setUsername("custom-login");

        MailAccount updated = service.update(account, req);

        assertEquals("new@example.com", updated.getEmail());
        assertEquals("custom-login", updated.getUsername());
    }

    @Test
    @DisplayName("update：servers.incoming 整体替换 host/port，protocol/encryption 缺省保留现值")
    void test_update_serversPartialReplace() {
        MailAccount account = ownedAccount();
        AccountUpdateRequest req = new AccountUpdateRequest();
        AccountUpdateRequest.Servers servers = new AccountUpdateRequest.Servers();
        AccountUpdateRequest.Incoming in = new AccountUpdateRequest.Incoming();
        in.setHost("imap.new.com");
        in.setPort(143);
        servers.setIncoming(in);
        req.setServers(servers);

        MailAccount updated = service.update(account, req);

        assertEquals("imap.new.com", updated.getIncomingHost());
        assertEquals(143, updated.getIncomingPort());
        assertEquals("imap", updated.getIncomingProtocol(), "protocol 缺省应保留现值");
        assertEquals("ssl", updated.getIncomingEncryption(), "encryption 缺省应保留现值");
        assertEquals("smtp.example.com", updated.getOutgoingHost(), "outgoing 未出现不动");
    }

    @Test
    @DisplayName("update：isDefault=true 先清除其他账户默认再置位；isDefault=false 允许无默认")
    void test_update_isDefaultToggle() {
        MailAccount account = ownedAccount();
        AccountUpdateRequest req = new AccountUpdateRequest();
        req.setIsDefault(true);
        doNothing().when(service).clearOtherDefaults(anyString(), anyString(), anyString(), any());

        MailAccount updated = service.update(account, req);

        verify(service).clearOtherDefaults(eq("acc-001"), eq(USER_ID), eq(USER_NAME), any());
        assertEquals(1, updated.getIsDefault());

        MailAccount account2 = ownedAccount();
        account2.setIsDefault(1);
        AccountUpdateRequest req2 = new AccountUpdateRequest();
        req2.setIsDefault(false);
        MailAccount updated2 = service.update(account2, req2);
        assertEquals(0, updated2.getIsDefault(), "显式 isDefault=false 允许无默认账户");
    }

    @Test
    @DisplayName("update DTO 校验：空白字段/端口越界/非法协议加密被拒绝")
    void test_update_validate() {
        AccountUpdateRequest req = new AccountUpdateRequest();
        req.setName("  ");
        assertEquals("账户显示名不能为空白", req.validate());

        req = new AccountUpdateRequest();
        req.setPassword(" ");
        assertEquals("邮箱密码/授权码不能为空白", req.validate());

        req = new AccountUpdateRequest();
        AccountUpdateRequest.Servers servers = new AccountUpdateRequest.Servers();
        AccountUpdateRequest.Outgoing out = new AccountUpdateRequest.Outgoing();
        out.setHost("smtp.x.com");
        out.setPort(70000);
        servers.setOutgoing(out);
        req.setServers(servers);
        assertEquals("发信服务器端口非法（1-65535）", req.validate());

        servers = new AccountUpdateRequest.Servers();
        AccountUpdateRequest.Incoming in = new AccountUpdateRequest.Incoming();
        in.setHost("imap.x.com");
        in.setPort(993);
        in.setProtocol("pop3s");
        servers.setIncoming(in);
        req.setServers(servers);
        assertEquals("收信协议仅支持 imap/pop3", req.validate());

        in.setProtocol("pop3");
        in.setEncryption("starttls2");
        assertEquals("收信加密仅支持 ssl/tls/none", req.validate());

        in.setEncryption("tls");
        assertNull(req.validate(), "合法 servers 配置应通过校验");
        assertTrue(new AccountUpdateRequest().isEmpty(), "空体应被识别为无可更新字段");
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete：级联逻辑删除计数透传 + 账户审计字段 + 默认账户触发接任")
    void test_delete_cascadeAndAudit() {
        MailAccount account = ownedAccount();
        account.setIsDefault(1);
        doReturn(3).when(service).logicalDeleteMessages(eq("acc-001"), eq(USER_ID), eq(USER_NAME), any());
        doReturn(1).when(service).logicalDeleteCustomFolders(eq("acc-001"), eq(USER_ID), eq(USER_NAME), any());
        doNothing().when(service).promoteEarliestAsDefault(anyString(), anyString(), anyString(), any());

        Map<String, Integer> cascade = service.delete(account);

        assertEquals(3, cascade.get("cascadeMessages"));
        assertEquals(1, cascade.get("cascadeFolders"));
        ArgumentCaptor<MailAccount> captor = ArgumentCaptor.forClass(MailAccount.class);
        verify(dynamicDao).save(captor.capture());
        MailAccount saved = captor.getValue();
        // B4 修复后 delStatus 不再为常量 1，而是行 id 派生的正整数（acc-001 走 hashCode 兜底）
        assertTrue(saved.getDelStatus() > 0, "账户须逻辑删除（delStatus>0；B4 后为行 id 派生值）");
        assertEquals("acc-001".hashCode() & 0x7FFFFFFF, saved.getDelStatus(),
                "非数字 id 应退化为 hashCode & 0x7FFFFFFF（确定性，可断言语义）");
        assertNotNull(saved.getDeleteAt());
        assertEquals(USER_ID, saved.getUpdater());
        // 首参必须为被删账户 id：@Transactional 下 MetaQuery 读不到未提交删除写入，
        // 不显式排除会把已删账户自身选为继任者并全列回写覆盖删除（R1 冒烟实证）
        verify(service).promoteEarliestAsDefault(eq("acc-001"), eq(USER_ID), eq(USER_NAME), any());
    }

    @Test
    @DisplayName("delete：非默认账户不触发接任")
    void test_delete_nonDefaultSkipsPromote() {
        MailAccount account = ownedAccount();
        doReturn(0).when(service).logicalDeleteMessages(anyString(), anyString(), anyString(), any());
        doReturn(0).when(service).logicalDeleteCustomFolders(anyString(), anyString(), anyString(), any());

        service.delete(account);

        verify(service, never()).promoteEarliestAsDefault(anyString(), anyString(), anyString(), any());
    }

    // ==================== B4：del_status 唯一删除标记 ====================

    @Test
    @DisplayName("delete：B4 雪花 id 取低 31 位派生 del_status，同 (user,email) 两行不撞 UK")
    void test_delete_delStatusDerivedFromNumericSnowflakeId() {
        // 真实雪花 id（19 位数字串），同 user 同 email 两行 → 删除标记必须不同（修复前均为常量 1 撞 UK）
        MailAccount first = ownedAccount();
        first.setId("1876543210987654321");
        MailAccount second = ownedAccount();
        second.setId("1876543210987654322");
        doReturn(0).when(service).logicalDeleteMessages(anyString(), anyString(), anyString(), any());
        doReturn(0).when(service).logicalDeleteCustomFolders(anyString(), anyString(), anyString(), any());

        service.delete(first);
        service.delete(second);

        ArgumentCaptor<MailAccount> captor = ArgumentCaptor.forClass(MailAccount.class);
        verify(dynamicDao, org.mockito.Mockito.times(2)).save(captor.capture());
        List<MailAccount> saved = captor.getAllValues();
        int firstMarker = saved.get(0).getDelStatus();
        int secondMarker = saved.get(1).getDelStatus();

        assertTrue(firstMarker > 0, "删除标记必须为正整数（0 保留给未删语义）");
        assertTrue(secondMarker > 0, "删除标记必须为正整数（0 保留给未删语义）");
        assertTrue(firstMarker != secondMarker,
                "同 (user,email) 两行 id 不同 → 删除标记必须不同，否则仍撞 uk_user_email（B4 核心断言）");
        // 雪花 id 低 31 位（确定可推导）
        assertEquals((int) (Long.parseLong("1876543210987654321") & 0x7FFFFFFFL), firstMarker);
        assertEquals((int) (Long.parseLong("1876543210987654322") & 0x7FFFFFFFL), secondMarker);
    }

    @Test
    @DisplayName("delete：B4 非数字 id 退化为 hashCode & 0x7FFFFFFF（确定性，空安全）")
    void test_delete_delStatusFallbackForNonNumericId() {
        // 边界：测试桩/异常路径下 id 可能非数字（如 "acc-001"），必须仍产出合法正整数
        MailAccount account = ownedAccount();
        doReturn(0).when(service).logicalDeleteMessages(anyString(), anyString(), anyString(), any());
        doReturn(0).when(service).logicalDeleteCustomFolders(anyString(), anyString(), anyString(), any());

        service.delete(account);

        ArgumentCaptor<MailAccount> captor = ArgumentCaptor.forClass(MailAccount.class);
        verify(dynamicDao).save(captor.capture());
        int marker = captor.getValue().getDelStatus();
        assertTrue(marker > 0, "非数字 id 兜底仍须为正整数");
        assertEquals("acc-001".hashCode() & 0x7FFFFFFF, marker);
    }
}
