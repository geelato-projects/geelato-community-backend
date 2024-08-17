package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author geemeta
 *
 */
@Setter
@Getter
public class Token {
    private int index;
    private String value;
    private TokenType tokenType;
    /**
     * -- GETTER --
     *  若不是关键字，则为null，e.g. for if
     */
    private String keyword;
    /**
     * -- GETTER --
     *  tokenType!=TokenType.Value时才有值
     *
     * @return 关键字后面的表达式
     */
    private String express;

    /**
     * 若无参数，则为null
     * -- GETTER --
     *  key  :paramName
     *  value:param script,e.g. $
     * {
     * paramName
     * }
     *

     */
    private Map<String, String> params;


    @Override
    public String toString() {
        return value;
    }

}
