package cn.geelato.alert.support;

import java.util.List;
import java.util.Map;

/**
 * 告警事件源清单（规则 {@code match_json.source} 的合法取值域）。
 *
 * <p>告警规则不与任何业务模块强绑定：匹配条件以 {@code source} 自描述事件源，
 * 结构由源决定（EXCEPTION 源为 {@code {source,events,ruleCodes}}）。
 * 新增源 = 此处登记常量并补齐契约分支（门禁校验/模板上下文/记录回查）。
 */
public final class AlertSources {

    private AlertSources() {
    }

    /** 异常生命周期事件源（platform_exception 事件流）。 */
    public static final String EXCEPTION = "EXCEPTION";

    /** 已知源清单（门禁/端点校验的合法取值域，未知源硬失败）。 */
    public static final List<String> KNOWN = List.of(EXCEPTION);

    /** 源 → 对应实体/表名（ORM Hook 载荷 entityName/tableName 匹配用，事件形态统一走框架 ORM 事件/ORM Hook 机制）。 */
    private static final Map<String, String> ENTITY_OF_SOURCE = Map.of(EXCEPTION, "platform_exception");

    /** 实体/表名 → 源类型；未登记返回 null（调用方硬失败）。 */
    public static String sourceOfEntity(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return null;
        }
        for (Map.Entry<String, String> e : ENTITY_OF_SOURCE.entrySet()) {
            if (e.getValue().equalsIgnoreCase(entityName.trim())) {
                return e.getKey();
            }
        }
        return null;
    }
}
