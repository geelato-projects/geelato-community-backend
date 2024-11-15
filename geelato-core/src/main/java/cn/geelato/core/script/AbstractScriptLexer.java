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
     * 对输入的脚本行进行简单的词法分析。
     * <p>
     * 该方法将输入的脚本行（List<String> lines）解析成多个脚本语句（ScriptStatement）的列表。
     * 它通过遍历每一行，识别出语句标识符和内容行，并将它们分别存储到ScriptStatement对象中。
     * 如果某一行匹配到语句标识符的正则表达式，则会创建一个新的ScriptStatement对象，并设置其ID和内容列表。
     * 如果某一行是内容行，则会将该行添加到当前ScriptStatement对象的内容列表中。
     * 忽略以特定字符开头的注释行。
     * 最后，将包含脚本语句的列表返回。
     *
     * @param lines 要解析的脚本行列表
     * @return 解析后的脚本语句列表
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
                // 当前分行
                if (statementId != null) {
                    // 新的语句行，先保存已有的TemplateStatement
                    if (scriptStatement.getContent() != null && !scriptStatement.getContent().isEmpty()) {
                        scriptStatements.add(scriptStatement);
                    }
                }
                scriptStatement = new ScriptStatement();
                scriptStatement.setContent(new ArrayList<String>());
                statementId = parseStatementId(line);
                scriptStatement.setId(statementId);
                // 如果匹配的statementId行同时是内容行时
                if (statementIdLineIsContent()) {
                    scriptStatement.getContent().add(line);
                }
            } else {
                // 丢弃注解行，不进行add(line)
                switch (line.charAt(0)) {
                    case '*', '/':
                        continue;
                    case '-':
                        if (line.charAt(1) == '-') {
                            continue;
                        }
                    default:
                        break;
                }

                if (scriptStatement != null) {
                    scriptStatement.getContent().add(line);
                }
            }
        }
        // 添加最后一个
        if (scriptStatement != null && scriptStatement.getContent() != null && !scriptStatement.getContent().isEmpty()) {
            scriptStatements.add(scriptStatement);
        }
        return scriptStatements;
    }

    /**
     * 匹配的分割行的正则表达式
     */
    protected abstract Pattern getSplitPattern();

    /**
     * 匹配的分割行，statementId就在行内
     */
    protected abstract String parseStatementId(String matchedSplitLine);


    /**
     * 匹配行是否需加入有效的内容中(匹配行用于解析statementId，有时该行是
     * 有效的内容行，如js脚本的解析,function foo(){}是statementId行，
     * 同时也是内容行)。若是返回true，若不是返回false。
     */
    protected abstract boolean statementIdLineIsContent();
}
