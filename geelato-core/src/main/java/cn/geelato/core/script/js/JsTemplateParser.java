package cn.geelato.core.script.js;

import cn.geelato.core.script.AbstractParser;
import cn.geelato.core.script.ScriptStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author geemeta
 *
 */
public class JsTemplateParser extends AbstractParser<JsScriptLexer> {

    private static Logger logger = LoggerFactory.getLogger(JsTemplateParser.class);

    /**
     * @param lines javascript文件的行列表
     */
    @Override
    public Map<String, String> parse(List<String> lines) {
        HashMap<String, String> map = new HashMap<>();
        for (ScriptStatement ts : getLexer().lex(lines)) {
            map.put(ts.getId(), ts.getContentString());
            if (logger.isDebugEnabled()) {
                logger.debug(ts.getContentString());
            }
        }
        return map;
    }

    @Override
    public Class<JsScriptLexer> getLexerType() {
        return JsScriptLexer.class;
    }

}
