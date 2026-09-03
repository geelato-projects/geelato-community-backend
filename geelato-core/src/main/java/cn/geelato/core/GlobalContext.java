package cn.geelato.core;

@SuppressWarnings("ALL")
public class GlobalContext {
    protected final static String __ENV_ENCRYPT_TYPE__ = "GEELATO_ENCRYPT_TYPE";
    protected final static String __ENV_AES_KEY__ = "GEELATO_AES_KEY";
    protected final static String __ENV_SM4_KEY__ = "GEELATO_SM4_KEY";
    protected final static String __ENV_SM2_PUBLIC_KEY__ = "GEELATO_SM2_PUBLIC_KEY";
    protected final static String __ENV_SM2_PRIVATE_KEY__ = "GEELATO_SM2_PRIVATE_KEY";
    protected final static String __ENV_RSA_PUBLIC_KEY__ = "GEELATO_RSA_PUBLIC_KEY";
    protected final static String __ENV_RSA_PRIVATE_KEY__ = "GEELATO_RSA_PRIVATE_KEY";

    /*
    __SecurityLevel__代表系统密级，用于控制一些特殊的用于方便运维的越权手段等，但现在无实际意义。
     */
    protected final static Integer __SecurityLevel__ = 2;
    protected final static Boolean __ColumnEncrypt__ = __SecurityLevel__ > 0;
    protected final static Boolean __ApiEncrypt__ = __SecurityLevel__ > 1;
    protected final static String __EncryptType__ = "aes";
    protected final static String __AesKey__ = "b76278495b7f4df3";
    protected final static String __Sm4Key__ = "b76278495b7f4df3";
    protected final static String __Sm2PublicKey__ = "";
    protected final static String __Sm2PrivateKey__ = "";
    protected final static String __RsaPublicKey__ = "";
    protected final static String __RsaPrivateKey__ = "";
    protected final static String __Environment__ = "development";
    protected final static Boolean __CACHE__ = __Environment__.equals("product");
    /**
     * 异常响应是否携带 stackTraceDetail（技术详情+堆栈）。
     * 默认开启；如对外暴露场景不希望下发堆栈，可运行期调用 {@link #setLogStack(Boolean)} 关闭。
     */
    protected static Boolean __LogStack__ = true;
    protected final static Boolean __POLYGLOT_DEBUGGER__ =false;
    /** MQL 查询结果缓存全局开关;实体级门控见 EntityMeta.isBackEndCacheEnabled,两级同时开启才缓存 */
    protected final static Boolean __MetaQueryCache__ = true;
    /*
    在线文档站根地址，用于异常响应中拼接错误码文档链接（docUrl）。
    代码固化，如需替换部署域名，直接修改此处常量。
     */
    protected final static String __DocBaseUrl__ = "https://docs.geelato.cn";
    protected final static Boolean __DocUrlEnabled__ = true;
    /**
     * 默认租户编码：当登录链路无法确定用户租户（如 OAuth2 userinfo 未下发 tenantCode）时使用。
     * 可由环境变量 GEELATO_DEFAULT_TENANT 覆盖；默认 geelato，向后兼容历史硬编码。
     */
    protected final static String __DefaultTenantCode__ = "geelato";
    protected final static String __ENV_DEFAULT_TENANT__ = "GEELATO_DEFAULT_TENANT";
    public static String getEnvironment() {
        return __Environment__;
    }

    public static Boolean getColumnEncryptOption() {
        return __ColumnEncrypt__;
    }
    public static Boolean getApiEncryptOption() {
        return __ApiEncrypt__;
    }

    public static String getEncryptType() {
        return getEnvOrDefault(__ENV_ENCRYPT_TYPE__, __EncryptType__);
    }

    public static String getAesKey() {
        return getEnvOrDefault(__ENV_AES_KEY__, __AesKey__);
    }

    public static String getSm4Key() {
        return getEnvOrDefault(__ENV_SM4_KEY__, __Sm4Key__);
    }

    public static String getSm2PublicKey() {
        return getEnvOrDefault(__ENV_SM2_PUBLIC_KEY__, __Sm2PublicKey__);
    }

    public static String getSm2PrivateKey() {
        return getEnvOrDefault(__ENV_SM2_PRIVATE_KEY__, __Sm2PrivateKey__);
    }

    public static String getRsaPublicKey() {
        return getEnvOrDefault(__ENV_RSA_PUBLIC_KEY__, __RsaPublicKey__);
    }

    public static String getRsaPrivateKey() {
        return getEnvOrDefault(__ENV_RSA_PRIVATE_KEY__, __RsaPrivateKey__);
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

    public static void setLogStack(Boolean logStack) {
        if (logStack != null) {
            __LogStack__ = logStack;
        }
    }
    public static Boolean getMetaQueryCacheOption() {
        return __MetaQueryCache__;
    }

    /**
     * 在线文档站根地址（固化为 https://docs.geelato.cn）。
     * 异常处理器据此拼接错误码文档链接：{@code {baseUrl}/docs/reference/error-codes#{code}} 或
     * {@code {baseUrl}/docs/reference/error-codes/{slug}}。
     */
    public static String getDocBaseUrl() {
        return __DocBaseUrl__;
    }

    /**
     * 是否在异常响应中输出 docUrl 字段。
     */
    public static Boolean getDocUrlEnabled() {
        return __DocUrlEnabled__;
    }

    /**
     * 默认租户编码（OAuth2 等场景下用户租户未知时降级使用）。
     */
    public static String getDefaultTenantCode() {
        return getEnvOrDefault(__ENV_DEFAULT_TENANT__, __DefaultTenantCode__);
    }

    private static String getEnvOrDefault(String envName, String defaultValue) {
        String value = System.getenv(envName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
