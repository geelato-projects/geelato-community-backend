package cn.geelato.orm.value;

/**
 * Java 侧的特殊值引用表达。
 */
public record ValueRef(Type type, String expression) {

    public enum Type {
        CTX,
        FN,
        PARENT
    }
}
