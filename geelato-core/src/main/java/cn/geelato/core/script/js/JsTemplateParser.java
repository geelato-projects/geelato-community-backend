package cn.geelato.core.script.js;

import cn.geelato.core.script.AbstractParser;
import cn.geelato.core.script.ScriptStatement;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author geemeta
 */
@Slf4j
public class JsTemplateParser extends AbstractParser<JsScriptLexer> {


    /**
     * 解析给定的javascript文件行列表，并返回一个包含解析结果的Map。
     *
     * @param lines javascript文件的行列表，每一行代表文件中的一行内容。
     * @return 返回一个Map，其中键是解析出的标识符（ID），值是对应的内容字符串。
     */
    @Override
    public Map<String, String> parse(List<String> lines) {
        HashMap<String, String> map = new HashMap<>();
        for (ScriptStatement ts : getLexer().lex(lines)) {
            map.put(ts.getId(), ts.getContentString());
            if (log.isDebugEnabled()) {
                log.debug(ts.getContentString());
            }
        }
        return map;
    }

    @Override
    public Class<JsScriptLexer> getLexerType() {
        return JsScriptLexer.class;
    }

}
