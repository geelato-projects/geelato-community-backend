package cn.geelato.archive.enums;

/**
 * 归档策略类型。
 * <ul>
 *   <li>{@link #DEFAULT}：默认策略，条件固定为 {@code create_at < 当前时间 - retention_days}，时间水位在 run 开始时锁定；</li>
 *   <li>{@link #CUSTOM}：自定义策略，whereCondition 为任意 WHERE 条件片段（如 {@code batch < 'xxx'}），
 *       引擎执行时原样拼入选数查询，无时间概念。</li>
 * </ul>
 */
public enum PolicyTypeEnum {

    DEFAULT("默认策略（create_at 时间窗口）"),
    CUSTOM("自定义策略（任意 WHERE 条件）");

    private final String description;

    PolicyTypeEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static PolicyTypeEnum parse(String value) {
        for (PolicyTypeEnum e : values()) {
            if (e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return null;
    }
}
