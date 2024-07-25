package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;

import java.util.Map;

/**
 * @author geemeta
 *
 */
public class Token {
    private int index;
    private String value;
    private TokenType tokenType;
    private String keyword;
    private String express;

    /**
     * 若无参数，则为null
     */
    private Map<String, String> params;


    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    /**
     * 若不是关键字，则为null，e.g. for if
     */
    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    /**
     * tokenType!=TokenType.Value时才有值
     * @return 关键字后面的表达式
     */
    public String getExpress() {
        return express;
    }

    public void setExpress(String express) {
        this.express = express;
    }

    /**
     * key  :paramName
     * value:param script,e.g. ${paramName}
     *
     * @return
     */
    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }


    @Override
    public String toString() {
//       StringBuilder sb = new StringBuilder();
//       sb.append(value)
        return value;
    }

}
