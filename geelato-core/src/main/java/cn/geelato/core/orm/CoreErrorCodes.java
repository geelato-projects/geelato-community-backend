package cn.geelato.core.orm;

import cn.geelato.lang.exception.ErrorCode;
import lombok.Getter;

/**
 * geelato-core 模块的错误码枚举。
 * <p>覆盖 ORM/MQL/SQL 相关业务错误码，作为错误码元数据的唯一事实源。</p>
 *
 * <h3>编码规则</h3>
 * <p>保留历史码值不变（如 10008/10010/10011），仅做"枚举注册表化"治理。</p>
 *
 * @author geelato
 */
@Getter
public enum CoreErrorCodes implements ErrorCode {

    /** MQL JSON 解析异常。 */
    MQL_JSON_PARSE(10008, "MQL json解析异常"),

    /** SQL 执行异常。高频错误，提供独立详情页。 */
    SQL_EXECUTE(10010, "SQL执行异常") {
        @Override
        public String getDocSlug() {
            return "sql-execute";
        }
    },

    /** 实体过滤字段不存在。 */
    INVALID_FILTER_FIELD(10011, "实体过滤字段不存在");

    private final int code;
    private final String defaultMessage;

    CoreErrorCodes(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
