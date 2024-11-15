package cn.geelato.core.script.js;

import cn.geelato.core.script.AbstractScriptManager;
import lombok.extern.slf4j.Slf4j;
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
     * 解析*.js和*.sql文件
     * <p>
     * 该方法用于解析指定路径下的*.js和*.sql文件，支持两种文件格式。
     *
     * @param file 要解析的文件
     * @throws IOException 如果在文件读取或解析过程中发生输入输出异常，则抛出此异常
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
     * 根据给定的id和参数生成SQL语句。
     *
     * @param id       SQL语句的ID或者函数名称
     * @param paramMap 包含参数的Map，其中键为参数名，值为参数值。参数值可以是对象、字符串、数字等基本类型。
     * @return 返回生成的SQL语句字符串，如果生成失败则返回null。
     * @throws ScriptException       如果在执行JavaScript脚本时发生异常
     * @throws NoSuchMethodException 如果找不到对应的执行方法
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
