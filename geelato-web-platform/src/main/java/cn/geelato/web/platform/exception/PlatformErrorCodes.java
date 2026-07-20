package cn.geelato.web.platform.exception;

import cn.geelato.lang.exception.ErrorCode;
import lombok.Getter;

/**
 * geelato-web-platform 模块的错误码枚举。
 * <p>覆盖鉴权、插件、GQL、文件处理等业务错误码。鉴权类异常声明正确 HTTP 状态码（400/403）。</p>
 *
 * <h3>编码规则</h3>
 * <p>保留历史码值不变（如 10001/10003/20001/1200/1213-1218/400/403），仅做"枚举注册表化"治理。</p>
 *
 * @author geelato
 */
@Getter
public enum PlatformErrorCodes implements ErrorCode {

    // ---- 插件 ----
    /** 插件未找到。 */
    PLUGIN_NOT_FOUND(10001, "UnFoundPluginException"),

    // ---- GQL ----
    /** Gql 解析异常。 */
    GQL_RESOLVE(10003, "Gql Resolve Exception"),

    // ---- 鉴权（HTTP 状态码语义化）----
    /** 请求参数错误。HTTP 400。 */
    AUTH_BAD_REQUEST(400, "请求参数错误") {
        @Override
        public int getHttpStatus() {
            return 400;
        }
    },
    /** 无权操作该用户。HTTP 403。 */
    ACCOUNT_OPERATION_FORBIDDEN(403, "无权操作该用户") {
        @Override
        public int getHttpStatus() {
            return 403;
        }
    },
    /** 登录异常：多租户场景需选择租户。 */
    LOGIN_MULTI_TENANT(20001, "登录异常[MultiTenant]"),

    // ---- 文件处理（12xx 段，1200 为根，1213-1218 为子类）----
    /** 文件异常根码。 */
    FILE(1200, "12 File Exception"),
    /** 12.3 文件类型不支持。 */
    FILE_TYPE_NOT_SUPPORTED(1213, "12.3 File Type Not Support Exception"),
    /** 12.4 文件大小超出限制。 */
    FILE_SIZE_EXCEED_LIMIT(1214, "12.4 File Size Exceed Limit Exception"),
    /** 12.5 文件不存在。 */
    FILE_NOT_FOUND(1215, "12.5 File Not Found Exception"),
    /** 12.6 文件内容校验失败。 */
    FILE_CONTENT_VALID_FAILED(1216, "12.6 File Content Validate Failed Exception"),
    /** 12.7 文件内容为空。 */
    FILE_CONTENT_IS_EMPTY(1217, "12.7 File Content Is Empty Exception"),
    /** 12.8 文件内容读取失败。 */
    FILE_CONTENT_READ_FAILED(1218, "12.8 File Content Read Failed Exception");

    private final int code;
    private final String defaultMessage;

    PlatformErrorCodes(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
