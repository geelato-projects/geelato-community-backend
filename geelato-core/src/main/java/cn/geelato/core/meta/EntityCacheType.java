package cn.geelato.core.meta;

public enum EntityCacheType {
    BackEnd,
    FrontEnd,
    BackEndAndFrontEnd;

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
