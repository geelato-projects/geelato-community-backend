package cn.geelato.archive.enums;

/**
 * 归档目标存储类型。
 * <p>一期仅实现 {@link #MYSQL}；MONGODB/ELASTICSEARCH 为策略模型预留，通道实现二期接入
 * （对齐平台 Dialects 方言枚举的异构基因）。</p>
 */
public enum TargetTypeEnum {

    MYSQL("MySQL 归档库/归档表"),
    MONGODB("MongoDB（二期）"),
    ELASTICSEARCH("Elasticsearch（二期）");

    private final String description;

    TargetTypeEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** 一期开放的目标类型 */
    public static boolean supported(TargetTypeEnum type) {
        return type == MYSQL;
    }

    public static TargetTypeEnum parse(String value) {
        for (TargetTypeEnum e : values()) {
            if (e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return null;
    }
}
