package cn.geelato.core.script.rule;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.core.script.js.JsTemplateParser;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class BizRuleScriptManager extends AbstractScriptManager {

    private final JsTemplateParser jsTemplateParser = new JsTemplateParser();
    private final JsProvider jsProvider = new JsProvider();

    /**
     * 解析*.js的文件
     *
     */
    @Override
    public void parseFile(File file) throws IOException {
        compileJs(jsTemplateParser.parse(Files.readAllLines(Paths.get(file.getPath()))));
    }

    /**
     * 解析*.js的文件流
     *
     */
    @Override
    public void parseStream(InputStream inputStream) throws IOException {
        compileJs(jsTemplateParser.parse(readLines(inputStream)));
    }

    private void compileJs(Map<String, String> jsFuncMap) {
        try {
            jsProvider.compile(jsFuncMap);
        } catch (ScriptException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void loadDb() {

    }


    /**
     * @param functionName functionName
     * @param paramMap     paramMap中put的key与函数的参数名称需一致，{@link Bindings}
     * @return 执行结果
     */
    public Object execute(String functionName, Map<String, Object> paramMap) {
        if (jsProvider.contain(functionName)) {
            try {
                Object result = jsProvider.execute(functionName, paramMap);
                if (log.isInfoEnabled()) {
                    log.info("execute {} : {}", functionName, result);
                }
                return result;
            } catch (ScriptException | NoSuchMethodException e) {
                log.error("脚本执行失败。function:{}。", functionName, e);
                return null;
            }
        } else {
            log.error("未找到function：{}，对应的函数。", functionName);
            return null;
        }
    }


}
