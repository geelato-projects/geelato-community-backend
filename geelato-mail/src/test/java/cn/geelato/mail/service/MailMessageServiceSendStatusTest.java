package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MailMessageService 发送/撤回状态持久化单元测试（P1 第二批，V76 列）。
 *
 * 覆盖 7 项场景：
 * - saveSentCopy（2 项）：send_status='sent' 落库 + 附件元数据 contentType 优先真实 MIME
 *   / contentType 缺失回退前端 type 枚举
 * - saveFailedCopy（3 项）：send_status='failed' + send_error 摘要落库 / message_id 为 null
 *   / send_error 超 500 字符截断
 * - markWithdrawFailed（1 项）：withdraw_status='failed' + 审计字段刷新
 * - attachmentMetadata（1 项）：脏 JSON fail-fast IllegalStateException
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailMessageServiceSendStatusTest {

    @InjectMocks
    private MailMessageService service;

    @Mock
    private Dao dynamicDao;

    @Mock
    private MailLabelService labelService;

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";

    @BeforeEach
    void setUp() {
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

    private MailAccount buildAccount() {
        MailAccount account = new MailAccount();
        account.setId("account-001");
        account.setName("张三的企业邮");
        account.setEmail("zhangsan@example.com");
        return account;
    }

    private MailMessageService.ComposeRequest buildCompose(List<MailMessageService.AttachmentDto> attachments) {
        MailMessageService.ComposeRequest compose = new MailMessageService.ComposeRequest();
        MailMessageService.AddressDto to = new MailMessageService.AddressDto();
        to.setName("李四");
        to.setEmail("lisi@example.com");
        compose.setTo(List.of(to));
        compose.setSubject("周报");
        compose.setContent("<p>本周进展</p>");
        compose.setAttachments(attachments);
        return compose;
    }

    private MailMessageService.AttachmentDto attachment(String name, String type, String contentType, String token) {
        MailMessageService.AttachmentDto att = new MailMessageService.AttachmentDto();
        att.setName(name);
        att.setSize(1024L);
        att.setType(type);
        att.setContentType(contentType);
        att.setToken(token);
        return att;
    }

    // ==================== saveSentCopy ====================

    @Test
    @DisplayName("saveSentCopy：send_status='sent' 落库，附件 contentType 优先真实 MIME 且携带 token")
    void test_saveSentCopy_persistsSentStatus() {
        when(dynamicDao.save(any(MailMessage.class))).thenReturn(Map.of("id", "copy-001"));

        String copyId = service.saveSentCopy(buildAccount(),
                buildCompose(List.of(attachment("报表.xlsx", "xls",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        USER_ID + "/202608/abc123"))),
                "<msg-001@example.com>");

        assertEquals("copy-001", copyId);
        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(dynamicDao, times(1)).save(captor.capture());
        MailMessage saved = captor.getValue();
        assertEquals("sent", saved.getFolder());
        assertEquals("sent", saved.getSendStatus());
        assertNull(saved.getSendError());
        assertNull(saved.getWithdrawStatus());
        assertEquals("<msg-001@example.com>", saved.getMessageId());
        assertEquals(1, saved.getHasAttachment());
        String json = saved.getAttachmentsJson();
        assertNotNull(json);
        assertTrue(json.contains("spreadsheetml.sheet"), "应存真实 MIME 类型而非前端枚举: " + json);
        assertTrue(json.contains("\"token\":\"" + USER_ID + "/202608/abc123\""), "应携带上传 token: " + json);
        assertEquals(USER_ID, saved.getCreator());
        assertEquals(TENANT_CODE, saved.getTenantCode());
    }

    @Test
    @DisplayName("saveSentCopy：附件 contentType 缺失 → contentType 回退前端 type 枚举（向后兼容）")
    void test_saveSentCopy_contentTypeFallbackToType() {
        when(dynamicDao.save(any(MailMessage.class))).thenReturn(Map.of("id", "copy-002"));

        service.saveSentCopy(buildAccount(),
                buildCompose(List.of(attachment("报表.xlsx", "xls", null, null))),
                null);

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(dynamicDao).save(captor.capture());
        String json = captor.getValue().getAttachmentsJson();
        assertNotNull(json);
        assertTrue(json.contains("\"contentType\":\"xls\""), "contentType 缺失时应回退 type 枚举: " + json);
        assertTrue(!json.contains("token"), "无 token 附件不得写入 token 键: " + json);
    }

    // ==================== saveFailedCopy ====================

    @Test
    @DisplayName("saveFailedCopy：send_status='failed' + send_error 摘要落库，无 SMTP Message-ID")
    void test_saveFailedCopy_persistsFailure() {
        when(dynamicDao.save(any(MailMessage.class))).thenReturn(Map.of("id", "copy-003"));

        String copyId = service.saveFailedCopy(buildAccount(), buildCompose(null), "535 Authentication failed");

        assertEquals("copy-003", copyId);
        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(dynamicDao).save(captor.capture());
        MailMessage saved = captor.getValue();
        assertEquals("sent", saved.getFolder(), "失败副本仍落发件箱供用户重发");
        assertEquals("failed", saved.getSendStatus());
        assertEquals("535 Authentication failed", saved.getSendError());
        assertNull(saved.getMessageId(), "未发出邮件无 SMTP Message-ID");
        assertEquals(0, saved.getHasAttachment());
    }

    @Test
    @DisplayName("saveFailedCopy：send_error 超 500 字符截断（列宽 512 留余量）")
    void test_saveFailedCopy_errorTruncated() {
        when(dynamicDao.save(any(MailMessage.class))).thenReturn(Map.of("id", "copy-004"));
        String longError = "SMTP 错误 " + "x".repeat(600);

        service.saveFailedCopy(buildAccount(), buildCompose(null), longError);

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(dynamicDao).save(captor.capture());
        assertEquals(500, captor.getValue().getSendError().length(), "send_error 应截断至 500 字符");
    }

    // ==================== markWithdrawFailed ====================

    @Test
    @DisplayName("markWithdrawFailed：withdraw_status='failed' 落库 + 更新人审计字段刷新")
    void test_markWithdrawFailed() {
        MailMessage msg = new MailMessage();
        msg.setId("copy-005");
        msg.setFolder("sent");
        msg.setUserId(USER_ID);

        service.markWithdrawFailed(msg);

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(dynamicDao).save(captor.capture());
        MailMessage saved = captor.getValue();
        assertEquals("failed", saved.getWithdrawStatus());
        assertEquals(USER_ID, saved.getUpdater());
        assertEquals(USER_NAME, saved.getUpdaterName());
        assertNotNull(saved.getUpdateAt());
    }

    // ==================== attachmentMetadata ====================

    @Test
    @DisplayName("attachmentMetadata：脏 JSON fail-fast IllegalStateException（不静默兜底空列表）")
    void test_attachmentMetadata_dirtyJson_failFast() {
        MailMessage msg = new MailMessage();
        msg.setAttachmentsJson("{not-a-json-array");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> service.attachmentMetadata(msg)).getMessage().contains("附件元数据 JSON 解析失败"));
    }

    // ==================== toDate ====================

    @Test
    @DisplayName("toDate：LocalDateTime（MySQL 8 驱动 DATETIME 实际返回类型）正确转 Date，拒绝静默置 null")
    void test_toDate_localDateTime() {
        java.time.LocalDateTime ldt = java.time.LocalDateTime.of(2026, 8, 12, 10, 41, 33);
        java.util.Date converted = MailMessageService.toDate(ldt);
        assertNotNull(converted);
        assertEquals(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant(), converted.toInstant());
    }

    @Test
    @DisplayName("toDate：Date/Timestamp 直通、null 归 null、未知类型 fail-fast")
    void test_toDate_passthroughAndFailFast() {
        java.util.Date now = new java.util.Date();
        assertEquals(now, MailMessageService.toDate(now));
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2026-08-12 10:41:33");
        assertEquals(ts, MailMessageService.toDate(ts));
        assertNull(MailMessageService.toDate(null));
        assertThrows(IllegalStateException.class, () -> MailMessageService.toDate("2026-08-12"));
    }
}
