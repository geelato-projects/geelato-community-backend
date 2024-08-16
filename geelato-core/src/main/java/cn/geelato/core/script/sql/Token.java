package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author geemeta
 */
@Getter
@Setter
public class Token {
    private int index;
    private String value;
    private TokenType tokenType;
    // 若不是关键字，则为null，e.g. for if
    private String keyword;
    // tokenType!=TokenType.Value时才有值 关键字后面的表达式
    private String express;
    // 若无参数，则为null ,key  :paramName, value:param script,e.g. ${paramName}
    private Map<String, String> params;

    @Override
    public String toString() {
        // StringBuilder sb = new StringBuilder();
        // sb.append(value)
        return value;
    }
}
