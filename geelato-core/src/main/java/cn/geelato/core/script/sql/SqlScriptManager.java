package cn.geelato.core.script.sql;

import cn.geelato.core.script.AbstractScriptManager;
import cn.geelato.core.script.js.JsProvider;
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
public class SqlScriptManager extends AbstractScriptManager {

    private final SqlScriptParser sqlScriptParser = new SqlScriptParser();
    private final JsProvider jsProvider = new JsProvider();


    /**
     * 解析*.js、*.sql的文件，支持两种格式
     *
     * @param file 待解析的文件
     * @throws IOException 解析异常
     */
    @Override
    public void parseFile(File file) throws IOException {
        compileJs(sqlScriptParser.parse(Files.readAllLines(Paths.get(file.getPath()))));
    }

    /**
     * 解析InputStream格式
     *
     * @param inputStream 待解析的文件流
     * @throws IOException 解析异常
     */
    @Override
    public void parseStream(InputStream inputStream) throws IOException {
        compileJs(sqlScriptParser.parse(readLines(inputStream)));
    }

    private void compileJs(Map<String, String> jsFuncMap) {
        try {
            jsProvider.compile(jsFuncMap);
        } catch (ScriptException e) {
            log.error("", e);
        }
    }


    /**
     * 根据提供的sqlId或函数名以及参数Map生成SQL语句。
     *
     * @param id       sqlId或函数名
     * @param paramMap 包含键值对的Map，其中键为字符串类型，值为Object类型，可以是对象、字符串或数字等基本类型
     * @return 返回生成的SQL语句字符串，如果生成失败则返回null
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
}
