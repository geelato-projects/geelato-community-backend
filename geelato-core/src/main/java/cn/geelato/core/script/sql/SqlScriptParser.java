
package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;
import cn.geelato.core.script.AbstractParser;
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
public class SqlScriptParser extends AbstractParser<SqlScriptLexer> {
    private static Logger logger = LoggerFactory.getLogger(SqlScriptParser.class);
    private Map<String, String> sqlMap = null;
    public final static String VAL_FLAG = "$";
    public final static String VAL_NAME = "$";
    //e.g. match $tableName or $tableName.item or $addList[i].type.XX.ZZ
    private final static Pattern PATTERN_VAL_FLAG = Pattern.compile("[\\s]*\\$\\.[\\w,\\[,\\]]+[\\.]?\\w*[\\.]?\\w*[\\.]?\\w*");

    @Override
    public Map<String, String> parse(List<String> lines) {
        HashMap<String, List<Token>> map = getLexer().lexDeep(lines);
        sqlMap = new HashMap<>(map.size());
        map.entrySet().forEach(entry -> {
            try {
                sqlMap.put(entry.getKey(), toJsFunction(entry.getKey(), parseTokens(entry.getValue())));
                if (logger.isDebugEnabled()) {
                    logger.debug("-- @sql " + entry.getKey());
                    logger.debug("\r\n" + sqlMap.get(entry.getKey()));
                }
            } catch (Exception e) {
                logger.error("", e);
            }
            logger.debug("sqlMap:", sqlMap.size());
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
        switch (tabCount) {
            case (1):
                return "  ";
            case (2):
                return "    ";
            case (3):
                return "      ";
            default:
                return "  ";
        }
    }

    private String toJsFunction(String sqlId, String content) {
        StringBuilder jsFunc = new StringBuilder();
        jsFunc.append("function " + sqlId + "(");
        jsFunc.append(VAL_NAME);
        jsFunc.append("){\r\n");
        jsFunc.append("  var sql = new Array();");
        jsFunc.append("\r\n");
        jsFunc.append(content);
//        jsFunc.append("\r\n");
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
            //TokenType.Open
            switch (token.getKeyword()) {
                case ("for"):
                    return parseFor(token.getValue());
                case ("if"):
                    return parseIf(token.getValue());
                default:
                    throw new Exception("未支持的关键字：" + token.getKeyword());
            }
        }
    }

    /**
     * @param template
     * @return e.g. CREATE TABLE IF NOT EXISTS "+param.tableName+" (id bigint(20) "
     */
    public String replace(String template, boolean isJsCode) {
        Matcher matcher = PATTERN_VAL_FLAG.matcher(template);
        if (matcher.find()) {
            int startIndex = template.indexOf(VAL_FLAG, matcher.start());

            if (isJsCode) {
                if (VAL_NAME.equals(VAL_FLAG)) {
                    return template;
                }
                StringBuilder sb = new StringBuilder(template);
                sb.replace(startIndex, startIndex + 1, VAL_NAME);
                return replace(sb.toString(), isJsCode);
            } else {
                StringBuilder sb = new StringBuilder();
                if (startIndex > 0) {
                    sb.append("\"");
                    sb.append(template.substring(0, startIndex));
                    sb.append("\"+");
                }
                sb.append(matcher.group().replace(VAL_FLAG, VAL_NAME));
                String unReplace = template.substring(matcher.end());
                if (StringUtils.hasText(unReplace)) {
                    sb.append("+\"");
                    sb.append(replace(unReplace, isJsCode));
//                    sb.append("\"");
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
     * @return
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
//                    String item = indexItem[1];
//                    String itemVal = val + "[" + index + "]";

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
//                    sb.append(item);
//                    sb.append("=");
//                    sb.append(itemVal);
//                    sb.append(";");
                    return new JsToken(true, sb.toString(), TokenType.Open);
                }
            }
        }
        throw new Exception("格式不正确，格式应如：@for i,item in $addList，当前格式内容为：" + template);
    }

    /**
     * @param template e.g. @if $addList[i].defaultValue!='' && $addList[i].defaultValue!=null
     * @return
     * @throws Exception
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
