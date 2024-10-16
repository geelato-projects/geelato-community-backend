package cn.geelato.core.script;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * @author geemeta
 */
@Slf4j
public abstract class AbstractParser<E extends AbstractScriptLexer> {

    protected E lexer;

    /**
     * @param lines toParse lines
     * @return key:sqlId or functionName,value:jsFunctionText
     */
    public abstract Map<String, String> parse(List<String> lines);

    public abstract Class<E> getLexerType();

    @SuppressWarnings("unchecked")
    protected <T extends E> T getLexer() {
        if (lexer == null) {
            try {
                lexer = getLexerType().getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                log.error(e.getMessage(), e);
            }
        }
        return (T) lexer;
    }
}
