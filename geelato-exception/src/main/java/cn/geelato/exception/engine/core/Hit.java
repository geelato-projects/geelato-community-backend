package cn.geelato.exception.engine.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 管道最终返回的命中（hits 契约的类型化载体）：契约只保留引擎语义字段——bizId 幂等键、
 * bizType 回溯坐标（脚本返回优先、规则值兜底）、title/snapshot 展示与留证、
 * bizLabel 可读标识、anchorTime 时间锚点；
 * level 恒取规则值不进命中；处理人/部门等人事字段与业务字段（单号/客户等）一律 extras
 * （BIZ_FIELD 显式 fields 声明按名取值、TRIAL 回显）。
 * 序列化保持契约全字段（null 显式回显），便于消费方对齐结构。
 */
@Getter
public class Hit {

    /** 契约字段（bizType 例外：fromMap 提取脚本值，空时引擎以规则值兜底）。 */
    private static final Set<String> CONTRACT_FIELDS = Set.of(
            "bizType", "bizId", "bizLabel", "title", "anchorTime", "snapshot");

    /** 回溯坐标之一：脚本返回优先，PipelineEngine 空值时以 rule.biz_type 兜底。 */
    private String bizType;
    /** 唯一必填：命中对象标识。 */
    private final String bizId;
    /** 业务可读标识（工作台列表认对象、URL 模板 {bizLabel} 占位）。 */
    private final String bizLabel;
    /** 实例标题（脚本组装单号/客户/金额等），缺省兜底规则名。 */
    private final String title;
    /** yyyy-MM-dd HH:mm:ss 或 ISO 本地时间。 */
    private final String anchorTime;
    /** 业务快照，原样进实例 detail_json。 */
    private final Object snapshot;

    private final Map<String, Object> extras = new LinkedHashMap<>();

    private Hit(String bizType, String bizId, String bizLabel,
                String title, String anchorTime, Object snapshot) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.bizLabel = bizLabel;
        this.title = title;
        this.anchorTime = anchorTime;
        this.snapshot = snapshot;
    }

    /** camelCase 归一化后的命中对象 → Hit（bizType 提取脚本值可空；契约外字段进 extras）。 */
    public static Hit fromMap(Map<String, Object> map) {
        Hit hit = new Hit(str(map.get("bizType")), str(map.get("bizId")), str(map.get("bizLabel")),
                str(map.get("title")), str(map.get("anchorTime")), map.get("snapshot"));
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!CONTRACT_FIELDS.contains(entry.getKey())) {
                hit.extras.put(entry.getKey(), entry.getValue());
            }
        }
        return hit;
    }

    /** 仅引擎兜底用：bizType 为空时以规则定义补值（脚本返回值优先，不覆盖）。 */
    public void defaultBizType(String bizType) {
        if (this.bizType == null || this.bizType.isBlank()) {
            this.bizType = bizType;
        }
    }

    /** 按名统一取值（BIZ_FIELD 选择器与抑制 keyFields 消费）：契约字段走 getter，其余查 extras，未命中返回 null。 */
    public Object getField(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return switch (name) {
            case "bizType" -> bizType;
            case "bizId" -> bizId;
            case "bizLabel" -> bizLabel;
            case "title" -> title;
            case "anchorTime" -> anchorTime;
            case "snapshot" -> snapshot;
            default -> extras.get(name);
        };
    }

    @JsonAnyGetter
    public Map<String, Object> getExtras() {
        return extras;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
