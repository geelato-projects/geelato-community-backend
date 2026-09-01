package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.mail.contact.service.MailContactRecentService;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.service.MailAccountService;
import cn.geelato.mail.service.MailAttachmentStorageService;
import cn.geelato.mail.service.MailFilterService;
import cn.geelato.mail.service.MailMessageService;
import cn.geelato.mail.service.MailProtocolService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MailController 单元测试（ST-20 O-1）。
 *
 * 锁定 sync 端点凭据解密失败的响应壳与 send/source/attachments 同构：
 * HTTP 200 + ApiResult code 50000，IllegalStateException 不再落全局异常处理器
 * （修复前为 HTTP 400 + code=-2，与 send 的 200/50000 不一致，前端拦截器无法按
 * 既有业务码路径统一提示）。
 */
class MailControllerTest {

    private MailController controller;
    private MailAccountService accountService;
    private MailProtocolService protocolService;

    @BeforeEach
    void setUp() {
        accountService = mock(MailAccountService.class);
        protocolService = mock(MailProtocolService.class);
        controller = new MailController();
        ReflectionTestUtils.setField(controller, "accountService", accountService);
        ReflectionTestUtils.setField(controller, "messageService", mock(MailMessageService.class));
        ReflectionTestUtils.setField(controller, "protocolService", protocolService);
        ReflectionTestUtils.setField(controller, "attachmentStorageService", mock(MailAttachmentStorageService.class));
        ReflectionTestUtils.setField(controller, "contactRecentService", mock(MailContactRecentService.class));
        ReflectionTestUtils.setField(controller, "filterService", mock(MailFilterService.class));
    }

    @Test
    @DisplayName("sync：凭据解密失败返回 50000 业务码（与 send 同构），不触发 IMAP 拉取")
    void sync_decryptFailure_returns50000() throws MessagingException {
        MailAccount account = new MailAccount();
        account.setId("a1");
        account.setEmail("user@example.com");
        when(accountService.getOwned("a1")).thenReturn(account);
        when(accountService.decryptPassword(account))
                .thenThrow(new IllegalStateException("邮箱凭据解密失败（KEK 不匹配或密文损坏）"));

        ApiResult<Map<String, Object>> result = controller.sync("a1");

        assertEquals(50000, result.getCode());
        verify(protocolService, never()).fetchInbox(any(), anyString());
    }
}
