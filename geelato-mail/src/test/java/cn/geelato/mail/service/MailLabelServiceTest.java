package cn.geelato.mail.service;

import cn.geelato.mail.entity.MailLabel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MailLabelService 响应契约单元测试（ST-29 标签 unreadCount 序列化失配修复）。
 *
 * 覆盖场景：
 * - toResponse：unreadCount 以 Integer 放入响应 Map（Jackson 输出 JSON number 的直接证据）
 * - toResponse：在与生产 JacksonConfig 相同的 Long→String 全局序列化配置下，
 *   unreadCount 仍输出 JSON number（不带引号），id 雪花仍为 string（契约级回归证据）
 * - toResponse：sortOrder（int 实体字段）输出 JSON number
 * - toEmbeddedResponse：不含 unreadCount（MailItem.labels 内嵌契约不变）
 */
class MailLabelServiceTest {

    private final MailLabelService service = new MailLabelService();

    private MailLabel newLabel() {
        MailLabel label = new MailLabel();
        label.setId("1234567890123456789");
        label.setName("工作");
        label.setColor("#165dff");
        label.setSortOrder(3);
        return label;
    }

    /** 与生产 JacksonConfig.longToStringCustomizer 等价的 ObjectMapper（Long/long → String） */
    private ObjectMapper productionLikeMapper() {
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.module.SimpleModule module =
                new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    @DisplayName("toResponse：unreadCount 为 Integer（Jackson 输出 JSON number，前端 z.number() 契约对齐）")
    void toResponseUnreadCountIsInteger() {
        Map<String, Object> map = service.toResponse(newLabel(), 5L);
        assertInstanceOf(Integer.class, map.get("unreadCount"),
                "unreadCount 须为 int/Integer，long 会被 JacksonConfig 全局序列化为字符串");
        assertEquals(5, map.get("unreadCount"));
    }

    @Test
    @DisplayName("toResponse：生产同款 Long→String 序列化配置下 unreadCount 输出 JSON number、id 保持 string")
    void toResponseSerializesUnreadCountAsJsonNumber() throws Exception {
        String json = productionLikeMapper().writeValueAsString(service.toResponse(newLabel(), 5L));
        JsonNode node = new ObjectMapper().readTree(json);
        assertTrue(node.get("unreadCount").isNumber(),
                "unreadCount 须为 JSON number（ST-29：long 会被全局序列化为 \"5\" 字符串，前端校验抛错）");
        assertEquals(5, node.get("unreadCount").asInt());
        assertTrue(node.get("id").isTextual(), "id 雪花须保持 string（防 JS Number 精度溢出）");
        assertEquals("1234567890123456789", node.get("id").asText());
        assertTrue(node.get("sortOrder").isNumber(), "sortOrder 为 int 实体字段，输出 JSON number");
    }

    @Test
    @DisplayName("toResponse：创建场景 unreadCount=0 同样输出 JSON number（创建即校验路径）")
    void toResponseZeroUnreadCountIsJsonNumber() throws Exception {
        String json = productionLikeMapper().writeValueAsString(service.toResponse(newLabel(), 0L));
        JsonNode node = new ObjectMapper().readTree(json);
        assertTrue(node.get("unreadCount").isNumber());
        assertEquals(0, node.get("unreadCount").asInt());
    }

    @Test
    @DisplayName("toEmbeddedResponse：内嵌契约不含 unreadCount（列表/详情展示结构不变）")
    void toEmbeddedResponseOmitsUnreadCount() {
        Map<String, Object> map = service.toEmbeddedResponse(newLabel());
        assertFalse(map.containsKey("unreadCount"));
        assertEquals("1234567890123456789", map.get("id"));
        assertEquals(3, map.get("sortOrder"));
    }
}
