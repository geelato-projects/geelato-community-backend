package cn.geelato.alert.engine.deliver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** route_json {@code {default:{channels,templateCode}}} 解析（唯一通道组，无级别路由）。 */
public final class AlertRoute {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AlertRoute() {
    }

    /** 通道组（route.default.channels）；route_json 缺失/畸形返回空清单（调用方按无人可投硬失败）。 */
    public static List<String> channels(String routeJson) {
        JsonNode node = routeJson == null || routeJson.isBlank() ? null : parse(routeJson);
        JsonNode channels = node == null ? null : node.path("default").path("channels");
        List<String> out = new ArrayList<>();
        if (channels != null && channels.isArray()) {
            for (JsonNode c : channels) {
                String v = c.asText(null);
                if (v != null && !v.isBlank()) {
                    out.add(v.trim());
                }
            }
        }
        return out;
    }

    /** 模板编码（route.default.templateCode）；缺失返回 null。 */
    public static String templateCode(String routeJson) {
        JsonNode node = routeJson == null || routeJson.isBlank() ? null : parse(routeJson);
        if (node == null) {
            return null;
        }
        String code = node.path("default").path("templateCode").asText(null);
        return code == null || code.isBlank() ? null : code;
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
