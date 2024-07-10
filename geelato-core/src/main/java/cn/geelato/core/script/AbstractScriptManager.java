package cn.geelato.core.script;


import cn.geelato.core.orm.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author geemeta
 */
public abstract class AbstractScriptManager {

    protected Dao dao;
    private static final Logger logger = LoggerFactory.getLogger(AbstractScriptManager.class);
    protected static final String RN_WIN = "\r\n";   // in WinOS
    protected static final String RN_LINUX = "\n";    // in Linux
    protected ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * @param dao {@link Dao}
     */
    public void setDao(Dao dao) {
        this.dao = dao;
    }

    /**
     * 使用之前需先{@link #setDao(Dao)}
     *
     * @param sqlId
     */
    public abstract void loadDb(String sqlId);

    /**
     * @param path 文件存放目录,多个目录用逗号分隔，递归加载子目录
     * @throws IOException 读取文件出错
     */
    public void loadFiles(String path) throws IOException {
        String[] paths = path.split(",");
        for (String p : paths) {
            parseDirectory(new File(p));
        }
    }

    /**
     * @param file 文件存放目录,递归加载子目录
     * @throws IOException IOException
     */
    public void loadFiles(File file) throws IOException {
        parseDirectory(file);
    }

    protected void parseDirectory(File file) throws IOException {
        Assert.isTrue(file.exists(), "不存在的目录：" + file.getPath());
        File[] files = file.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                parseDirectory(f);
            } else {
                parseFile(f);
            }
        }
    }

    public abstract void parseFile(File file) throws IOException;


    public abstract void parseStream(InputStream is) throws IOException;


    public void loadResource(String locationPattern) throws IOException {
        try {
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                // 获得文件流，因为在jar文件中，不能直接通过文件资源路径拿到文件，但是可以在jar包中拿到文件流
                InputStream is = resource.getInputStream();
                parseStream(is);
            }
        } catch (IOException e) {
            logger.error("加载、处理数据（" + locationPattern + "）失败。", e);
        }
    }

    public List<String> readLines(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        List<String> lineList = new ArrayList<>();
        String line = null;
        while ((line = reader.readLine()) != null) {
            lineList.add(line);
            logger.debug("line:{}", line);
        }
        return lineList;
    }
}
