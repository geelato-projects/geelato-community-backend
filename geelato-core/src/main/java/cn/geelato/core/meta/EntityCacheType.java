package cn.geelato.core.meta;

public enum EntityCacheType {
    None,
    BackEnd,
    FrontEnd,
    BackEndAndFrontEnd;

    /** 服务端缓存启用:BackEnd / BackEndAndFrontEnd */
    public boolean backEndEnabled() {
        return this == BackEnd || this == BackEndAndFrontEnd;
    }

    public static EntityCacheType fromStringIgnoreCase(String value) {
        if (value == null) {
            throw new IllegalArgumentException("EntityCacheType is must not null");
        }
        for (EntityCacheType type : EntityCacheType.values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "No matching enum value found for input: " + value +
                        ", available values: " + java.util.Arrays.toString(EntityCacheType.values())
        );
    }
}
