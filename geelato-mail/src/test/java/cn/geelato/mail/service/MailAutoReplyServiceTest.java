package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.mail.contact.entity.MailContact;
import cn.geelato.mail.contact.service.MailContactService;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailAutoReplyLog;
import cn.geelato.mail.entity.MailFilter;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.entity.MailVacation;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MailAutoReplyService 单元测试（Step B1 自动回复真实发送）。
 *
 * 覆盖场景：
 * - sendFilterReply：成功发送 + 台账落库 / 24h 频率跳过 / 自发自收跳过 / 内容空白跳过 / 主题大小写归一化
 * - sendVacationReplyBatch：未启用 / 时间窗外 / onlyContacts 非联系人 / 成功 + lastSentAt 回写 / 24h 频率跳过
 * - 发送失败（MessagingException）：不抛出、不写台账（不阻断同步主流程）
 */
class MailAutoReplyServiceTest {

    private static final String USER_ID = "user-001";
    private static final String USER_NAME = "张三";
    private static final String TENANT_CODE = "geelato";
    private static final String ACCOUNT_EMAIL = "me@example.com";
    private static final String SENDER_EMAIL = "boss@example.com";

    private MailAutoReplyService service;
    private Dao dynamicDao;
    private MailProtocolService protocolService;
    private MailVacationService vacationService;
    private MailContactService contactService;

    @BeforeEach
    void setUp() {
        dynamicDao = mock(Dao.class);
        protocolService = mock(MailProtocolService.class);
        vacationService = mock(MailVacationService.class);
        contactService = mock(MailContactService.class);
        service = spy(new MailAutoReplyService());
        ReflectionTestUtils.setField(service, "dynamicDao", dynamicDao);
        ReflectionTestUtils.setField(service, "protocolService", protocolService);
        ReflectionTestUtils.setField(service, "vacationService", vacationService);
        ReflectionTestUtils.setField(service, "contactService", contactService);
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

    // ==================== fixtures ====================

    private MailAccount newAccount() {
        MailAccount account = new MailAccount();
        account.setId("acc-1");
        account.setEmail(ACCOUNT_EMAIL);
        account.setName("Me");
        return account;
    }

    /** 发件人地址故意大小写混合，验证归一化 */
    private MailMessage newMail() {
        MailMessage msg = new MailMessage();
        msg.setId("mail-1");
        msg.setFromEmail("Boss@Example.com");
        msg.setFromName("Boss");
        msg.setSubject("Price Inquiry");
        return msg;
    }

    private MailFilter newFilter() {
        MailFilter filter = new MailFilter();
        filter.setId("filter-1");
        filter.setName("客户自动回复");
        return filter;
    }

    private MailVacation newVacation(int onlyContacts) {
        MailVacation cfg = new MailVacation();
        cfg.setId("vac-1");
        cfg.setUserId(USER_ID);
        cfg.setEnabled(1);
        cfg.setSubject("休假自动回复");
        cfg.setContent("我在休假中\n节后回复您");
        cfg.setOnlyContacts(onlyContacts);
        return cfg;
    }

    // ==================== sendFilterReply ====================

    @Test
    @DisplayName("sendFilterReply：成功发送（Re: 原主题 + 文本转 HTML）并写台账（发件人小写归一化）")
    void filterReply_sendsAndWritesLedger() throws MessagingException {
        doReturn(null).when(service).latestSentAt(anyString(), anyString(), anyString());

        service.sendFilterReply(newAccount(), "plain-pwd", newMail(), newFilter(), "收到，尽快处理");

        ArgumentCaptor<MailProtocolService.ComposeMail> composeCaptor =
                ArgumentCaptor.forClass(MailProtocolService.ComposeMail.class);
        verify(protocolService).send(any(MailAccount.class), eq("plain-pwd"), composeCaptor.capture());
        MailProtocolService.ComposeMail compose = composeCaptor.getValue();
        assertEquals("Re: Price Inquiry", compose.subject());
        assertEquals(1, compose.to().size());
        assertEquals(SENDER_EMAIL, compose.to().get(0).email());
        assertEquals("Boss", compose.to().get(0).name());
        assertEquals("收到，尽快处理", compose.htmlContent());

        ArgumentCaptor<MailAutoReplyLog> ledgerCaptor = ArgumentCaptor.forClass(MailAutoReplyLog.class);
        verify(dynamicDao).save(ledgerCaptor.capture());
        MailAutoReplyLog ledger = ledgerCaptor.getValue();
        assertEquals(USER_ID, ledger.getUserId());
        assertEquals(SENDER_EMAIL, ledger.getSenderEmail());
        assertEquals("filter", ledger.getReplyType());
        assertEquals("filter-1", ledger.getRefId());
        assertEquals("mail-1", ledger.getMailId());
        assertEquals("Re: Price Inquiry", ledger.getReplySubject());
        assertEquals(TENANT_CODE, ledger.getTenantCode());
    }

    @Test
    @DisplayName("sendFilterReply：24h 内同发件人同过滤器已回复 → 跳过发送")
    void filterReply_skipsWithinInterval() throws MessagingException {
        doReturn(new Date()).when(service).latestSentAt(SENDER_EMAIL, "filter", "filter-1");

        service.sendFilterReply(newAccount(), "plain-pwd", newMail(), newFilter(), "收到");

        verify(protocolService, never()).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
        verify(dynamicDao, never()).save(any(MailAutoReplyLog.class));
    }

    @Test
    @DisplayName("sendFilterReply：超过 24h 的旧台账不拦截（25h 前记录 → 正常发送）")
    void filterReply_sendsAfterInterval() throws MessagingException {
        Date old = new Date(System.currentTimeMillis() - 25L * 60 * 60 * 1000);
        doReturn(old).when(service).latestSentAt(SENDER_EMAIL, "filter", "filter-1");

        service.sendFilterReply(newAccount(), "plain-pwd", newMail(), newFilter(), "收到");

        verify(protocolService, times(1)).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    @Test
    @DisplayName("sendFilterReply：自发自收（发件人=账户邮箱，大小写不同）→ 跳过防回环")
    void filterReply_skipsSelfSent() throws MessagingException {
        MailMessage msg = newMail();
        msg.setFromEmail("ME@example.com");

        service.sendFilterReply(newAccount(), "plain-pwd", msg, newFilter(), "收到");

        verify(protocolService, never()).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    @Test
    @DisplayName("sendFilterReply：回复内容空白 / 发件人地址为空 → 跳过")
    void filterReply_skipsBlankContentOrSender() throws MessagingException {
        service.sendFilterReply(newAccount(), "plain-pwd", newMail(), newFilter(), "   ");
        MailMessage noSender = newMail();
        noSender.setFromEmail(null);
        service.sendFilterReply(newAccount(), "plain-pwd", noSender, newFilter(), "收到");

        verify(protocolService, never()).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    // ==================== sendVacationReplyBatch ====================

    @Test
    @DisplayName("sendVacationReplyBatch：未配置或已停用 → 零发送（不查联系人）")
    void vacation_disabledSkips() throws MessagingException {
        doReturn(null).when(vacationService).findOwned();
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        MailVacation disabled = newVacation(0);
        disabled.setEnabled(0);
        doReturn(disabled).when(vacationService).findOwned();
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        verify(protocolService, never()).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
        verify(contactService, never()).listEntities(any());
    }

    @Test
    @DisplayName("sendVacationReplyBatch：时间窗外（未开始/已结束）→ 零发送")
    void vacation_outsideTimeWindowSkips() throws MessagingException {
        MailVacation future = newVacation(0);
        future.setStartTime(new Date(System.currentTimeMillis() + 86400000L));
        doReturn(future).when(vacationService).findOwned();
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        MailVacation past = newVacation(0);
        past.setEndTime(new Date(System.currentTimeMillis() - 86400000L));
        doReturn(past).when(vacationService).findOwned();
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        verify(protocolService, never()).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    @Test
    @DisplayName("sendVacationReplyBatch：onlyContacts 开启时非联系人跳过、联系人正常回复")
    void vacation_onlyContactsGuard() throws MessagingException {
        doReturn(newVacation(1)).when(vacationService).findOwned();
        doReturn(null).when(service).latestSentAt(anyString(), anyString(), anyString());
        MailContact contact = new MailContact();
        contact.setEmail("boss@example.com");
        doReturn(List.of(contact)).when(contactService).listEntities(null);

        // 联系人在通讯录 → 发送
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));
        verify(protocolService, times(1)).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));

        // 非通讯录发件人 → 跳过
        MailMessage stranger = newMail();
        stranger.setFromEmail("stranger@example.com");
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(stranger));
        verify(protocolService, times(1)).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    @Test
    @DisplayName("sendVacationReplyBatch：成功发送（配置主题 + 换行转 br）+ 台账 + lastSentAt 回写")
    void vacation_sendsAndTouchesLastSentAt() throws MessagingException {
        doReturn(newVacation(0)).when(vacationService).findOwned();
        doReturn(null).when(service).latestSentAt(anyString(), anyString(), anyString());

        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        ArgumentCaptor<MailProtocolService.ComposeMail> composeCaptor =
                ArgumentCaptor.forClass(MailProtocolService.ComposeMail.class);
        verify(protocolService).send(any(MailAccount.class), eq("plain-pwd"), composeCaptor.capture());
        MailProtocolService.ComposeMail compose = composeCaptor.getValue();
        assertEquals("休假自动回复", compose.subject());
        assertEquals("我在休假中<br>节后回复您", compose.htmlContent());

        ArgumentCaptor<MailAutoReplyLog> ledgerCaptor = ArgumentCaptor.forClass(MailAutoReplyLog.class);
        verify(dynamicDao).save(ledgerCaptor.capture());
        assertEquals("vacation", ledgerCaptor.getValue().getReplyType());
        assertEquals("", ledgerCaptor.getValue().getRefId());
        verify(vacationService).touchLastSentAt(any(Date.class));
    }

    @Test
    @DisplayName("sendVacationReplyBatch：配置主题空白时回退 Re: 原主题")
    void vacation_blankSubjectFallsBackToRe() throws MessagingException {
        MailVacation cfg = newVacation(0);
        cfg.setSubject("  ");
        doReturn(cfg).when(vacationService).findOwned();
        doReturn(null).when(service).latestSentAt(anyString(), anyString(), anyString());

        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        ArgumentCaptor<MailProtocolService.ComposeMail> composeCaptor =
                ArgumentCaptor.forClass(MailProtocolService.ComposeMail.class);
        verify(protocolService).send(any(MailAccount.class), anyString(), composeCaptor.capture());
        assertEquals("Re: Price Inquiry", composeCaptor.getValue().subject());
    }

    @Test
    @DisplayName("sendVacationReplyBatch：24h 内已回复同发件人 → 跳过（不同发件人互不影响）")
    void vacation_skipsWithinIntervalPerSender() throws MessagingException {
        doReturn(newVacation(0)).when(vacationService).findOwned();
        doReturn(new Date()).when(service).latestSentAt(SENDER_EMAIL, "vacation", "");
        doReturn(null).when(service).latestSentAt("alice@example.com", "vacation", "");

        MailMessage alice = newMail();
        alice.setFromEmail("alice@example.com");
        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail(), alice));

        // 仅 alice 发送，boss 被频率拦截
        verify(protocolService, times(1)).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    @Test
    @DisplayName("sendVacationReplyBatch：正文空白 → 零发送")
    void vacation_blankContentSkips() throws MessagingException {
        MailVacation cfg = newVacation(0);
        cfg.setContent("");
        doReturn(cfg).when(vacationService).findOwned();

        service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));

        verify(protocolService, never()).send(any(MailAccount.class), anyString(),
                any(MailProtocolService.ComposeMail.class));
    }

    // ==================== 失败语义 ====================

    @Test
    @DisplayName("发送失败（SMTP 异常）：不抛出、不写台账、不回写 lastSentAt（不阻断同步主流程）")
    void sendFailure_doesNotPropagate() throws MessagingException {
        doReturn(newVacation(0)).when(vacationService).findOwned();
        doReturn(null).when(service).latestSentAt(anyString(), anyString(), anyString());
        doThrow(new MessagingException("smtp connection refused"))
                .when(protocolService).send(any(MailAccount.class), anyString(),
                        any(MailProtocolService.ComposeMail.class));

        assertDoesNotThrow(() -> {
            service.sendFilterReply(newAccount(), "plain-pwd", newMail(), newFilter(), "收到");
            service.sendVacationReplyBatch(newAccount(), "plain-pwd", List.of(newMail()));
        });

        verify(dynamicDao, never()).save(any(MailAutoReplyLog.class));
        verify(vacationService, never()).touchLastSentAt(any(Date.class));
    }

    @Test
    @DisplayName("纯文本转 HTML：特殊字符转义（<&> 不注入标签）")
    void textToHtml_escapesSpecialChars() throws MessagingException {
        doReturn(null).when(service).latestSentAt(anyString(), anyString(), anyString());

        service.sendFilterReply(newAccount(), "plain-pwd", newMail(), newFilter(),
                "a < b & c > d");

        ArgumentCaptor<MailProtocolService.ComposeMail> composeCaptor =
                ArgumentCaptor.forClass(MailProtocolService.ComposeMail.class);
        verify(protocolService).send(any(MailAccount.class), anyString(), composeCaptor.capture());
        assertTrue(composeCaptor.getValue().htmlContent().contains("a &lt; b &amp; c &gt; d"));
    }
}
