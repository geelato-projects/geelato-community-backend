
package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;
import cn.geelato.core.script.AbstractParser;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将*.sql文件中的内容解析成javascript function，便于基于javascript的engine来创建语句
 *
 * @author geemeta
 */
@Slf4j
public class SqlScriptParser extends AbstractParser<SqlScriptLexer> {
    private Map<String, String> sqlMap = null;
    public final static String VAL_FLAG = "$";
    public final static String VAL_NAME = "$";
    //e.g. match $tableName or $tableName.item or $addList[i].type.XX.ZZ
    private final static Pattern PATTERN_VAL_FLAG = Pattern.compile("[\\s]*\\$\\.[\\w,\\[,\\]]+[\\.]?\\w*[\\.]?\\w*[\\.]?\\w*");

    @Override
    public Map<String, String> parse(List<String> lines) {
        HashMap<String, List<Token>> map = getLexer().lexDeep(lines);
        sqlMap = new HashMap<>(map.size());
        map.forEach((key, value) -> {
            try {
                sqlMap.put(key, toJsFunction(key, parseTokens(value)));
                if (log.isDebugEnabled()) {
                    log.debug("-- @sql {}", key);
                    log.debug("\r\n{}", sqlMap.get(key));
                }
            } catch (Exception e) {
                log.error("", e);
            }
            log.debug("sqlMap:{}", sqlMap.size());
        });
        return sqlMap;
    }

    @Override
    public Class<SqlScriptLexer> getLexerType() {
        return SqlScriptLexer.class;
    }

    private String parseTokens(List<Token> tokens) throws Exception {
        StringBuilder sb = new StringBuilder();
        int tabCount = 0;
        boolean isOpened = false;
        boolean isClosed = false;
        for (Token token : tokens) {
            JsToken jsToken = parseToken(token);
            if (jsToken.getType() == TokenType.Open) {
                tabCount++;
                isOpened = true;
                isClosed = false;
            } else if (jsToken.getType() == TokenType.Close) {
                tabCount--;
                isOpened = false;
                isClosed = true;
            } else {
//                isOpened = true;
            }
            if (jsToken.isJsCode()) {
                sb.append(tab(tabCount + (isClosed ? 1 : 0)));
                sb.append(jsToken.getValue());
            } else {
                sb.append(tab(tabCount + (isOpened ? 1 : 0)));
                sb.append("sql.push(");
                sb.append(jsToken.getValue());
                sb.append(")");
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private String tab(int tabCount) {
        return switch (tabCount) {
            case (2) -> "    ";
            case (3) -> "      ";
            default -> "  ";
        };
    }

    private String toJsFunction(String sqlId, String content) {
        StringBuilder jsFunc = new StringBuilder();
        jsFunc.append("function ").append(sqlId).append("(");
        jsFunc.append(VAL_NAME);
        jsFunc.append("){\r\n");
        jsFunc.append("  var sql = new Array();");
        jsFunc.append("\r\n");
        jsFunc.append(content);
        jsFunc.append("  return sql.join(' ');\r\n");
        jsFunc.append("}");
        return jsFunc.toString();
    }

    private JsToken parseToken(Token token) throws Exception {
        if (token.getTokenType() == TokenType.Value) {
            return new JsToken(false, replace(token.getValue(), false), TokenType.Value);
        } else if (token.getTokenType() == TokenType.Close) {
            return new JsToken(true, "}", TokenType.Close);
        } else {
            return switch (token.getKeyword()) {
                case ("for") -> parseFor(token.getValue());
                case ("if") -> parseIf(token.getValue());
                default -> throw new Exception("未支持的关键字：" + token.getKeyword());
            };
        }
    }

    /**
     * @return e.g. CREATE TABLE IF NOT EXISTS "+param.tableName+" (id bigint(20) "
     */
    public String replace(String template, boolean isJsCode) {
        Matcher matcher = PATTERN_VAL_FLAG.matcher(template);
        if (matcher.find()) {
            int startIndex = template.indexOf(VAL_FLAG, matcher.start());

            if (isJsCode) {
                return template;
            } else {
                StringBuilder sb = new StringBuilder();
                if (startIndex > 0) {
                    sb.append("\"");
                    sb.append(template, 0, startIndex);
                    sb.append("\"+");
                }
                sb.append(matcher.group().replace(VAL_FLAG, VAL_NAME));
                String unReplace = template.substring(matcher.end());
                if (StringUtils.hasText(unReplace)) {
                    sb.append("+\"");
                    sb.append(replace(unReplace, isJsCode));
                }
                return sb.toString().replaceAll("\"\"", "\"");
            }
        }
        if (isJsCode) {
            return template;
        }
        return "\"" + template + "\"";
    }

    /**
     * e.g. @for i,item in $addList
     *
     */
    public JsToken parseFor(String template) throws Exception {
        String[] w = template.split("[ ]+");
        //validate
        if ("@for".equals(w[0]) && ("in".equals(w[2]) || ":".equals(w[2]))) {
            //i
            String[] indexItem = w[1].split("[ ]*,[ ]*");
            if (indexItem.length == 1) {
                Matcher matcher = PATTERN_VAL_FLAG.matcher(w[3]);
                if (matcher.find()) {
                    //$addList
                    String val = replace(matcher.group().trim(), true);
                    String index = indexItem[0];

                    StringBuilder sb = new StringBuilder();
                    sb.append("for(var ");
                    sb.append(index);
                    sb.append("=0");
                    sb.append(";");
                    sb.append(index);
                    sb.append("<");
                    sb.append(val);
                    sb.append(".length");
                    sb.append(";");
                    sb.append(index);
                    sb.append("++");
                    sb.append("){");
                    return new JsToken(true, sb.toString(), TokenType.Open);
                }
            }
        }
        throw new Exception("格式不正确，格式应如：@for i,item in $addList，当前格式内容为：" + template);
    }

    /**
     * @param template e.g. @if $addList[i].defaultValue!='' && $addList[i].defaultValue!=null
     */
    public JsToken parseIf(String template) throws Exception {
        //TODO 多个关键字时怎么处理，需for
        String result = template.replace("@if", "").trim();
        StringBuilder sb = new StringBuilder();
        sb.append("if(");
        sb.append(replace(result, true));
        sb.append("){");
        return new JsToken(true, sb.toString(), TokenType.Open);
    }
}
