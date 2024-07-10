package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;

/**
 * @author geemeta
 *
 */
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

    public boolean isJsCode() {
        return jsCode;
    }

    public void setJsCode(boolean jsCode) {
        this.jsCode = jsCode;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public TokenType getType() {
        return type;
    }

    public void setType(TokenType type) {
        this.type = type;
    }
}
