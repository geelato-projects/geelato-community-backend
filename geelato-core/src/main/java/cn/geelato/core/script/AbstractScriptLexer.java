package cn.geelato.core.script;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author geemeta
 */
@Slf4j
public abstract class AbstractScriptLexer {

    /**
     * 简单解析，解析出模板内容段，便于下一步对模板内容段进一步解析
     *
     */
    public List<ScriptStatement> lex(List<String> lines) {
        String statementId = null;
        List<ScriptStatement> scriptStatements = new ArrayList<>();
        ScriptStatement scriptStatement = null;
        for (String l : lines) {
            String line = l.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = getSplitPattern().matcher(line);
            if (matcher.find()) {
                if (log.isDebugEnabled()) {
                    log.debug("matcher:{}", matcher.group());
                }
                //当前分行
                if (statementId != null) {
                    //新的语句行，先保存已有的TemplateStatement
                    if (scriptStatement.getContent() != null && !scriptStatement.getContent().isEmpty()) {
                        scriptStatements.add(scriptStatement);
                    }
                }
                scriptStatement = new ScriptStatement();
                scriptStatement.setContent(new ArrayList<String>());
                statementId = parseStatementId(line);
                scriptStatement.setId(statementId);
                //如果匹配的statementId行同时是内容行时
                if (statementIdLineIsContent()) {
                    scriptStatement.getContent().add(line);
                }
            } else {
                //丢弃注解行，不进行add(line)
                switch (line.charAt(0)) {
                    case '*', '/':
                        continue;
                    case '-':
                        if (line.charAt(1) == '-') {
                            continue;
                        }
                }

                if (scriptStatement != null) {
                    scriptStatement.getContent().add(line);
                }
            }
        }
        //添加最后一个
        if (scriptStatement != null && scriptStatement.getContent() != null && !scriptStatement.getContent().isEmpty()) {
            scriptStatements.add(scriptStatement);
        }
        return scriptStatements;
    }

    /**
     * 匹配的分割行的正则表达式
     *
     */
    protected abstract Pattern getSplitPattern();

    /**
     * 匹配的分割行，statementId就在行内
     *
     */
    protected abstract String parseStatementId(String matchedSplitLine);


    /**
     * 匹配行是否需加入有效的内容中(匹配行用于解析statementId，有时该行是
     * 有效的内容行，如js脚本的解析,function foo(){}是statementId行，
     * 同时也是内容行)。若是返回true，若不是返回false。
     *
     */
    protected abstract boolean statementIdLineIsContent();
}
