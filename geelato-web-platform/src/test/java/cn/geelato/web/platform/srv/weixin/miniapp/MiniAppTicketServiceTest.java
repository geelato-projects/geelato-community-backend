package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.miniapp.service.MiniAppTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * preBindTicket 签发与校验单元测试：往返成功、篡改签名拒绝、nonce 重放拒绝、密钥未配置拒绝。
 * 注：单测环境无 Redis，nonce 拉黑自动降级进程内 LocalBoundedCache（覆盖降级路径）。
 */
class MiniAppTicketServiceTest {

    private WeixinMiniAppConfigurationProperties properties;
    private MiniAppTicketService ticketService;

    @BeforeEach
    void setUp() {
        properties = new WeixinMiniAppConfigurationProperties();
        properties.setTicketKey("unit-test-ticket-key");
        properties.setTicketTtlSeconds(300);
        ticketService = new MiniAppTicketService(properties);
    }

    @Test
    void issue_verifyAndConsume_roundTripReturnsUserId() {
        String ticket = ticketService.issue("user-001", "13812341234");
        String userId = ticketService.verifyAndConsume(ticket);
        assertEquals("user-001", userId);
    }

    @Test
    void verify_tamperedPayloadRejected() {
        String ticket = ticketService.issue("user-001", "13812341234");
        int dot = ticket.indexOf('.');
        String payload = new String(java.util.Base64.getUrlDecoder().decode(ticket.substring(0, dot)));
        // 换绑他人账号（越权攻击形态）：payload 改为他人 userId，签名不匹配
        String forgedPayload = payload.replace("user-001", "user-999");
        String forgedTicket = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes()) + ticket.substring(dot);
        assertThrows(MiniAppTicketInvalidException.class, () -> ticketService.verifyAndConsume(forgedTicket));
    }

    @Test
    void verify_nonceReplayRejected() {
        String ticket = ticketService.issue("user-001", "13812341234");
        assertDoesNotThrow(() -> ticketService.verifyAndConsume(ticket));
        // 同一票据第二次消费：nonce 已拉黑
        assertThrows(MiniAppTicketInvalidException.class, () -> ticketService.verifyAndConsume(ticket));
    }

    @Test
    void verify_malformedTicketRejected() {
        assertThrows(MiniAppTicketInvalidException.class, () -> ticketService.verifyAndConsume(null));
        assertThrows(MiniAppTicketInvalidException.class, () -> ticketService.verifyAndConsume(""));
        assertThrows(MiniAppTicketInvalidException.class, () -> ticketService.verifyAndConsume("not-a-ticket"));
    }

    @Test
    void issue_ticketKeyMissingRejected() {
        properties.setTicketKey(null);
        WeixinMiniAppConfigurationProperties noKey = properties;
        MiniAppTicketService noKeyService = new MiniAppTicketService(noKey);
        assertThrows(MiniAppTicketInvalidException.class, () -> noKeyService.issue("user-001", "13812341234"));
    }

    @Test
    void phoneMatches_consistentDigestAcceptedAndMismatchRejected() {
        String ticket = ticketService.issue("user-001", "13812341234");
        int dot = ticket.indexOf('.');
        String payload = new String(java.util.Base64.getUrlDecoder().decode(ticket.substring(0, dot)));
        assertEquals(true, ticketService.phoneMatches(ticketService.extractPhoneDigest(payload), "13812341234"));
        assertEquals(false, ticketService.phoneMatches(ticketService.extractPhoneDigest(payload), "13900000000"));
    }
}
