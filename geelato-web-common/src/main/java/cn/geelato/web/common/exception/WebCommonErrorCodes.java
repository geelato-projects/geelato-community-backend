package cn.geelato.web.common.exception;

import cn.geelato.lang.exception.ErrorCode;
import lombok.Getter;

/**
 * geelato-web-common 模块的错误码枚举。
 * <p>覆盖鉴权链路上的通用业务错误码。鉴权类异常声明正确的 HTTP 状态码（401/...），
 * 使全局异常处理器返回符合语义的 HTTP 状态（而非统一 500）。</p>
 *
 * <h3>编码规则</h3>
 * <p>保留历史码值不变（如 401/10007），仅做"枚举注册表化"治理。
 * 注意 401 既是 HTTP 状态码又是业务码（历史遗留），声明 httpStatus=401 以保持语义一致。</p>
 *
 * @author geelato
 */
@Getter
public enum WebCommonErrorCodes implements ErrorCode {

    /** 未授权访问。HTTP 401。 */
    UNAUTHORIZED(401, "未授权访问[OAUTH]") {
        @Override
        public int getHttpStatus() {
            return 401;
        }
    },

    /** 令牌校验异常。 */
    INVALID_TOKEN(10007, "令牌校验异常[InvalidToken]");

    private final int code;
    private final String defaultMessage;

    WebCommonErrorCodes(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
