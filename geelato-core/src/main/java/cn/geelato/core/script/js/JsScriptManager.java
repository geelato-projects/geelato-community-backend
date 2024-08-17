package cn.geelato.core.script.js;

import cn.geelato.core.script.AbstractScriptManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 管理动态sql文件，解析并提供按sqlId、参数构建sql语句
 * 加载资源目录下的sql文件
 * TODO 加载数据库表中配置的sql
 * TODO 分析哪些是静态的语句块，解析成静态脚本，以便采用prepareStatement，提高sql执行性能
 *
 * @author geemeta
 */
@Slf4j
public class JsScriptManager extends AbstractScriptManager {
    private final JsTemplateParser jsTemplateParser = new JsTemplateParser();
    private final JsProvider jsProvider = new JsProvider();

    /**
     * 解析*.js、*.sql的文件，支持两种格式
     *
     */
    @Override
    public void parseFile(File file) throws IOException {
        compileJs(jsTemplateParser.parse(Files.readAllLines(Paths.get(file.getPath()))));
    }

    @Override
    public void parseStream(InputStream inputStream) throws IOException {
        compileJs(jsTemplateParser.parse(readLines(inputStream)));
    }

    private void compileJs(Map<String, String> jsFuncMap) {
        try {
            log(jsFuncMap);
            jsProvider.compile(jsFuncMap);
        } catch (ScriptException e) {
            log.error("", e);
        }
    }


    /**
     * @param id       sqlId or functionName
     * @param paramMap key value(key value)，值Object为key value的对象或字符串、数字等基本类型
     */
    public String generate(String id, Map<String, Object> paramMap) {
        if (jsProvider.contain(id)) {
            try {
                String sql = jsProvider.execute(id, paramMap).asString();
                if (log.isInfoEnabled()) {
                    log.info("sql {} : {}", id, sql);
                }
                return sql;
            } catch (ScriptException | NoSuchMethodException e) {
                log.error("sql脚本构建失败。", e);
                return null;
            }
        } else {
            Assert.isTrue(false, "未找到sqlId：" + id + "，对应的语句。");
            return null;
        }
    }

    @Override
    public void loadDb() {

    }

    private void log(Map<String, String> jsFuncMap) {
        if (log.isInfoEnabled()) {
            for (Map.Entry<String, String> entry : jsFuncMap.entrySet()) {
                log.info("将*.sql文件中的语句转换成javascript脚本，每个语名片段对应一个function");
                log.info("即sqlId：{} ，内容为:\r\n{}", entry.getKey(), entry.getValue());
            }
        }
    }
}
