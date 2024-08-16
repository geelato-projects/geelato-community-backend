package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;
import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Getter
@Setter
public class JsToken {
    private boolean jsCode;
    private String value;
    private TokenType type;

    public JsToken() {
    }

    public JsToken(boolean jsCode, String value, TokenType type) {
        setJsCode(jsCode);
        setValue(value);
        setType(type);
    }
}
