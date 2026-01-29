package cn.geelato.core;

@SuppressWarnings("ALL")
public class GlobalContext {

    /*
    __SecurityLevel__代表系统密级，用于控制一些特殊的用于方便运维的越权手段等，但现在无实际意义。
     */
    protected final static Integer __SecurityLevel__ = 2;
    protected final static Boolean __ColumnEncrypt__ = __SecurityLevel__ > 0;
    protected final static Boolean __ApiEncrypt__ = __SecurityLevel__ > 1;
    protected final static String __Environment__ = "development";
    protected final static Boolean __CACHE__ = __Environment__.equals("product");
    protected final static Boolean __LogStack__=false;
    protected final static Boolean __POLYGLOT_DEBUGGER__ =false;
    public static String getEnvironment() {
        return __Environment__;
    }

    public static Boolean getColumnEncryptOption() {
        return __ColumnEncrypt__;
    }
    public static Boolean getApiEncryptOption() {
        return __ApiEncrypt__;
    }

    public static Boolean getAnonymousOption() {
        return __SecurityLevel__ < 10;
    }

    public static String getAnonymousPwd() {
        return "H2k9ZpQ3@geElAto";
    }
    public static Boolean getLogStack(){
        return __LogStack__;
    }
}
