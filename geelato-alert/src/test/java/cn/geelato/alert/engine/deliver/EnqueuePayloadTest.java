package cn.geelato.alert.engine.deliver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnqueuePayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void payloadShapeMatchesMessageContract() throws Exception {
        String json = EnqueuePayload.ofUserIds("inapp", "标题", "内容",
                "rec:FL-1:inapp", List.of("u1", "u2")).toJson();
        JsonNode node = MAPPER.readTree(json);
        assertEquals("inapp", node.path("type").asText());
        assertEquals("fms-alert", node.path("sender").asText());
        assertEquals("alert", node.path("buss").asText());
        assertEquals("fms-alert", node.path("sourceSystem").asText());
        assertEquals("rec:FL-1:inapp", node.path("bizKey").asText());
        // receiver 为字符串化 JSON：{"type":"userId","list":["u1","u2"]}
        JsonNode receiver = MAPPER.readTree(node.path("receiver").asText());
        assertEquals("userId", receiver.path("type").asText());
        assertEquals("u1", receiver.path("list").get(0).asText());
        assertEquals("u2", receiver.path("list").get(1).asText());
    }
}
