package cn.geelato.core.script;

import java.util.List;
import java.util.Map;

/**
 * @author geemeta
 */
public abstract class AbstractParser<E extends AbstractScriptLexer> {

    protected E lexer;

    /**
     * @param lines toParse lines
     * @return key:sqlId or functionName,value:jsFunctionText
     */
    public abstract Map<String, String> parse(List<String> lines);

    public abstract Class<E> getLexerType();

    protected <T extends E> T getLexer() {
        if (lexer == null) {
            try {
                lexer = getLexerType().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return (T) lexer;
    }
}
