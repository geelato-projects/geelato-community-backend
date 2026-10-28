package cn.geelato.alert.engine.deliver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelRouterTest {

    @Test
    void sameNameChannelsPassThrough() {
        assertEquals("inapp", ChannelRouter.toMessageType("inapp"));
        assertEquals("email", ChannelRouter.toMessageType("email"));
        assertEquals("sms", ChannelRouter.toMessageType("sms"));
        assertEquals("bot", ChannelRouter.toMessageType("bot"));
    }

    @Test
    void weworkMapsToWeixinWorkGroup() {
        assertEquals("weixin_work_group", ChannelRouter.toMessageType("wework"));
    }

    @Test
    void voiceAndUnknownFailHard() {
        DeliverException ex = assertThrows(DeliverException.class, () -> ChannelRouter.toMessageType("voice"));
        assertTrue(ex.getMessage().contains("voice"));
        assertThrows(DeliverException.class, () -> ChannelRouter.toMessageType("wecom"));
        assertFalse(ChannelRouter.supports("voice"));
        assertTrue(ChannelRouter.supports("wework"));
    }
}
