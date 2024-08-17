package cn.geelato.core.script.sql;

import cn.geelato.core.enums.TokenType;
import cn.geelato.core.script.AbstractScriptLexer;
import cn.geelato.core.script.ScriptStatement;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author geemeta
 *
 */
@Slf4j
public class SqlScriptLexer extends AbstractScriptLexer {
    private static final String SQL_OPEN_FLAG = "@sql";
    private static final String KW_START_FLAG = "@";
    private static final String KW_END_FLAG = "@/";
    // -- @sql
    private static final Pattern splitPattern = Pattern.compile("[ ]*--[ ]*@sql[\\s]+");
    private static final Pattern keywordStartPattern = Pattern.compile("[ ]*@[\\w]+[\\s]*");
    private static final Pattern keywordEndPattern = Pattern.compile("[ ]*@[\\/\\w]+[\\s]*");

    public HashMap<String, List<Token>> lexDeep(List<String> list) {
        return lexSqlContent(lex(list));
    }


    public HashMap<String, List<Token>> lexSqlContent(List<ScriptStatement> scriptStatements) {
        HashMap<String, List<Token>> map = new HashMap<>(scriptStatements.size());
        for (ScriptStatement ScriptStatement : scriptStatements) {
            map.put(ScriptStatement.getId(), lexTokens(ScriptStatement.getContent()));
        }
        return map;
    }

    private List<Token> lexTokens(List<String> contentList) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        for (String l : contentList) {
            String line = l.trim();
            Token token = new Token();
            token.setValue(line);

            if (line.startsWith(KW_START_FLAG)) {
                Matcher startMatcher = keywordStartPattern.matcher(line);
                Matcher endMatcher = keywordEndPattern.matcher(line);
                if (startMatcher.find()) {
                    token.setTokenType(TokenType.Open);
                    token.setKeyword(startMatcher.group().replace(KW_START_FLAG, "").trim());
                } else if (endMatcher.find()) {
                    token.setTokenType(TokenType.Close);
                    token.setKeyword(endMatcher.group().replace(KW_END_FLAG, "").trim());
                } else {
                    token.setTokenType(TokenType.Value);
                    token.setKeyword("");
                }
            } else {
                token.setTokenType(TokenType.Value);
                token.setKeyword("");
            }

            token.setIndex(index++);
            tokens.add(token);
        }
        return tokens;
    }

    @Override
    protected Pattern getSplitPattern() {
        return splitPattern;
    }

    @Override
    protected String parseStatementId(String matchedSplitLine) {
        return matchedSplitLine.replace(SQL_OPEN_FLAG, "").replace("-", "").trim();
    }

    @Override
    protected boolean statementIdLineIsContent() {
        return false;
    }
}
