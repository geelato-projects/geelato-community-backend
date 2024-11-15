package cn.geelato.core.script.rule;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.core.script.js.JsTemplateParser;
import lombok.extern.slf4j.Slf4j;

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
     * 解析指定的*.js文件。
     *
     * @param file 要解析的*.js文件
     * @throws IOException 如果在读取文件或进行文件操作时发生I/O错误，将抛出此异常
     */
    @Override
    public void parseFile(File file) throws IOException {
        compileJs(jsTemplateParser.parse(Files.readAllLines(Paths.get(file.getPath()))));
    }

    /**
     * 解析*.js文件流
     * <p>
     * 从输入流中读取内容，并解析为JavaScript模板。
     *
     * @param inputStream 输入流，包含待解析的*.js文件内容
     * @throws IOException 如果在读取或解析输入流时发生I/O错误，将抛出此异常
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
     * 执行指定的JavaScript函数。
     *
     * @param functionName 要执行的JavaScript函数名称
     * @param paramMap     包含函数参数的Map，其中Map的key需要与函数参数名称一致。具体参数绑定方式请参考{@link Bindings}
     * @return 函数执行的结果，如果执行失败或未找到函数，则返回null
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
