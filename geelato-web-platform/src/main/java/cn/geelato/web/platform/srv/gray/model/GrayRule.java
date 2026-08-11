package cn.geelato.web.platform.srv.gray.model;

import lombok.Data;

/**
 * 灰度规则的轻量 POJO，仅用于 fastjson2 反序列化 Redis 中存放的规则 JSON，非持久化实体。
 * <p>
 * 规则示例：{@code {"targetType":"USER","targetValue":"zhangsan,lisi","priority":100}}
 */
@Data
public class GrayRule {

    /** 匹配维度：USER / ORG / DEPT / BU / TENANT / PERCENT / ALL */
    private String targetType;

    /** 匹配值，多值逗号分隔；PERCENT 时为 0~100。 */
    private String targetValue;

    /** 优先级，越大越先匹配，默认 0。 */
    private Integer priority = 0;

    /** 命中后写入的灰度标签，为空则使用全局 grayTag。 */
    private String grayTag;
}
