package cn.geelato.core.script;


import cn.geelato.core.GlobalContext;
import cn.geelato.core.orm.Dao;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author geemeta
 */
@Slf4j
public abstract class AbstractScriptManager extends GlobalContext {


    @Setter
    protected Dao dao;
    protected ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();


    public abstract void loadDb();

    /**
     * 加载指定路径下的文件。
     * <p>
     * 该方法接受一个包含文件存放目录路径的字符串作为参数，可以指定多个目录，目录之间用逗号分隔。
     * 方法会递归加载指定目录下的所有文件以及子目录中的文件。
     *
     * @param path 文件存放目录路径，支持指定多个目录，目录之间用逗号分隔
     * @throws IOException 如果在读取文件过程中发生错误，则抛出IOException异常
     */
    public void loadFiles(String path) throws IOException {
        String[] paths = path.split(",");
        for (String p : paths) {
            parseDirectory(new File(p));
        }
    }

    /**
     * 递归加载指定目录下的所有文件。
     *
     * @param file 文件存放目录，将递归加载该目录及其所有子目录中的文件。
     * @throws IOException 如果在文件操作过程中发生I/O错误，将抛出此异常。
     */
    public void loadFiles(File file) throws IOException {
        parseDirectory(file);
    }

    protected void parseDirectory(File file) throws IOException {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    parseDirectory(f);
                } else {
                    parseFile(f);
                }
            }
        }
    }

    public abstract void parseFile(File file) throws IOException;


    public abstract void parseStream(InputStream is) throws IOException;


    public void loadResource(String locationPattern) {
        try {
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                InputStream is = resource.getInputStream();
                parseStream(is);
            }
        } catch (IOException e) {
            log.error("加载、处理数据（{}）失败。", locationPattern, e);
        }
    }

    public List<String> readLines(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        List<String> lineList = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lineList.add(line);
            log.debug("line:{}", line);
        }
        return lineList;
    }

    // todo 内容校验
    protected Boolean validateContent(Object content) {
        return true;
    }
}
