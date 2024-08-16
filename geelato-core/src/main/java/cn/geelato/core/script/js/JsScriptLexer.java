package cn.geelato.core.script.js;

import cn.geelato.core.script.AbstractScriptLexer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.regex.Pattern;

/**
 * @author geemeta
 *
 */
@Slf4j
public class JsScriptLexer extends AbstractScriptLexer {

    private static Pattern splitPattern = Pattern.compile("[ ]*function[ ]+[\\w]*[ ]*\\(");

    @Override
    protected Pattern getSplitPattern() {
        return splitPattern;
    }

    @Override
    protected String parseStatementId(String matchedSplitLine) {
        int index = matchedSplitLine.indexOf("(");
        Assert.isTrue(index > 1, "格式有误：" + matchedSplitLine);
        return matchedSplitLine.substring(0, index).replace("function", "").trim();
    }

    @Override
    protected boolean statementIdLineIsContent() {
        return true;
    }
}
