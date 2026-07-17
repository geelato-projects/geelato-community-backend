package cn.geelato.core.meta;

/**
 * 同名实体（Java类源 vs DB在线源）冲突时的合并策略。
 * <p>
 * 当某个 entityName 同时存在于 Java 类注解（{@code @Entity(name=...)}）
 * 与数据库在线定义（{@code platform_dev_table.entity_name}）时，
 * 决定主缓存 {@code entityMetadataMap} 以哪一方为准。
 * </p>
 *
 * @author geemeta
 */
public enum ConflictStrategy {
    /**
     * 以在线DB定义为准（覆盖字段集）。
     * 在线实体由设计器维护，通常代表最新真值，适合低代码/动态建表场景。
     */
    DATABASE,
    /**
     * 以Java类定义为准（兼容历史行为）。
     * 适用 Java 实体作为权威定义、不允许被在线覆盖的场景。
     */
    CLASS
}
