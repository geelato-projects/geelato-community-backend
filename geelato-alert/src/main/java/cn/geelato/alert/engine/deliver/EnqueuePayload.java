package cn.geelato.alert.engine.deliver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * geelato-message POST /message/enqueue 请求体（契约见 docs/notification/integration-guide.md 附录 C）。
 * receiver 为字符串化 JSON（{"type":"userId","list":[...]}），type 单值（含逗号 message 侧直接拒绝）。
 */
public class EnqueuePayload {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String SENDER = "fms-alert";
    public static final String BUSS = "alert";
    public static final String SOURCE_SYSTEM = "fms-alert";

    private final String type;
    private final String title;
    private final String content;
    private final String bizKey;
    private final String receiverJson;

    private EnqueuePayload(String type, String title, String content, String bizKey, String receiverJson) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.bizKey = bizKey;
        this.receiverJson = receiverJson;
    }

    /** userId 多接收人载荷（告警拆条的统一形态：一通道一条消息、receiver.list 放全部接收人）。 */
    public static EnqueuePayload ofUserIds(String type, String title, String content,
                                           String bizKey, List<String> userIds) {
        return new EnqueuePayload(type, title, content, bizKey, receiverJson(userIds));
    }

    /** {"type":"userId","list":[...]} —— message 契约要求字符串化 JSON。 */
    public static String receiverJson(List<String> userIds) {
        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("type", "userId");
        receiver.put("list", userIds);
        try {
            return MAPPER.writeValueAsString(receiver);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("receiver 序列化失败", e);
        }
    }

    public String toJson() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("type", type);
        body.put("sender", SENDER);
        body.put("buss", BUSS);
        body.put("sourceSystem", SOURCE_SYSTEM);
        body.put("bizKey", bizKey);
        body.put("receiver", receiverJson);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("enqueue 请求体序列化失败", e);
        }
    }

    public String getType() {
        return type;
    }

    public String getBizKey() {
        return bizKey;
    }
}
