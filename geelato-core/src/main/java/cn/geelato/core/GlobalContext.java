package cn.geelato.core;

@SuppressWarnings("ALL")
public class GlobalContext {

    protected final static Integer __SecurityLevel__ = 0;
    protected final static Boolean __ColumnEncrypt__ = __SecurityLevel__ > 0;
    protected final static String __Environment__ = "development";
    protected final static Boolean __CACHE__ = __Environment__.equals("product");

    public static String getEnvironment() {
        return __Environment__;
    }

    public static Boolean getColumnEncrypt() {
        return __ColumnEncrypt__;
    }
}
