package cn.geelato.lang.exception;

import lombok.Getter;

/**
 * geelato-lang 模块的错误码枚举。
 * <p>本模块内的业务错误码以枚举常量形式集中声明，作为错误码元数据的唯一事实源。</p>
 *
 * <h3>编码规则</h3>
 * <p>保留历史码值不变（如 10006），仅做"枚举注册表化"治理，确保码值与文档锚点稳定。</p>
 *
 * @author geelato
 */
@Getter
public enum LangErrorCodes implements ErrorCode {

    /** 当前版本不支持该操作。 */
    UNSUPPORTED_VERSION(10006, "The current version does not support this operation！");

    private final int code;
    private final String defaultMessage;

    LangErrorCodes(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
