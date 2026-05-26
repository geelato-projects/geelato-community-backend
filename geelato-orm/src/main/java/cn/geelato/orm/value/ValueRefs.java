package cn.geelato.orm.value;

/**
 * 特殊值引用工厂。
 */
public final class ValueRefs {

    private ValueRefs() {
    }

    public static ValueRef ctx(String key) {
        return new ValueRef(ValueRef.Type.CTX, key);
    }

    public static ValueRef fn(String key) {
        return new ValueRef(ValueRef.Type.FN, key);
    }

    public static ValueRef fnNow() {
        return fn("now");
    }

    public static ValueRef fnNowDate() {
        return fn("nowDate");
    }

    public static ValueRef fnNowDateTime() {
        return fn("nowDateTime");
    }

    public static ValueRef parent(String key) {
        return new ValueRef(ValueRef.Type.PARENT, key);
    }
}
