package cn.geelato.core.script.rule;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.core.script.js.JsTemplateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.script.Bindings;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author geemeta
 */
public class BizRuleScriptManager extends AbstractScriptManager {

    private static final Logger logger = LoggerFactory.getLogger(BizRuleScriptManager.class);
    private final JsTemplateParser jsTemplateParser = new JsTemplateParser();
    private final JsProvider jsProvider = new JsProvider();

    /**
     * 解析*.js的文件
     *
     * @param file
     */
    @Override
    public void parseFile(File file) throws IOException {
        compileJs(jsTemplateParser.parse(Files.readAllLines(Paths.get(file.getPath()))));
    }

    /**
     * 解析*.js的文件流
     *
     * @param inputStream
     */
    @Override
    public void parseStream(InputStream inputStream) throws IOException {
        compileJs(jsTemplateParser.parse(readLines(inputStream)));
    }

    private void compileJs(Map<String, String> jsFuncMap) {
        try {
            jsProvider.compile(jsFuncMap);
        } catch (ScriptException e) {
            logger.error("", e);
        }
    }

    @Override
    public void loadDb(String sqlId) {

    }


    /**
     * @param functionName functionName
     * @param paramMap     paramMap中put的key与函数的参数名称需一致，{@link Bindings}
     * @return 执行结果
     * @throws ScriptException 脚本执行错误
     */
    public Object execute(String functionName, Map<String, Object> paramMap) {
        if (jsProvider.contain(functionName)) {
            try {
                Object result = jsProvider.execute(functionName, paramMap);
                if (logger.isInfoEnabled()) {
                    logger.info("execute {} : {}", functionName, result);
                }
                return result;
            } catch (ScriptException | NoSuchMethodException e) {
                logger.error("脚本执行失败。function:" + functionName + "。", e);
                return null;
            }
        } else {
            Assert.isTrue(false, "未找到function：" + functionName + "，对应的函数。");
            return null;
        }
    }


}
