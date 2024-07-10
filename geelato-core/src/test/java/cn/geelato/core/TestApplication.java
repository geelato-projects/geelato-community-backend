package cn.geelato.core;

import cn.geelato.core.biz.rules.BizManagerFactory;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.MetaRelf;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.DbGenerateDao;
import cn.geelato.core.orm.SqlFiles;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@SpringBootApplication
@ComponentScan(basePackages = {"org.geelato"})
public class TestApplication implements CommandLineRunner, InitializingBean {
    private static Logger logger = LoggerFactory.getLogger(TestApplication.class);
    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
//    @Qualifier("primaryDao")
    protected Dao dao;

    @Autowired
    protected DbGenerateDao dbGenerateDao;

    protected boolean isWinOS;
    

    private void assertOS() {
        Properties prop = System.getProperties();
        String os = prop.getProperty("os.name");
        logger.info("[操作系统]" + os);
        if (os.toLowerCase().startsWith("win"))
            isWinOS = true;
        else
            isWinOS = false;
    }

    @Override
    public void run(String... strings) throws Exception {
        System.out.println("配置文件：" + applicationContext.getEnvironment().getProperty("geelato.env"));
        logger.info("[启动应用]...");
        assertOS();
        initMeta();
        logger.info("[启动应用]...OK");
    }

    public void initMeta() throws IOException {
        // 解析元数据
        MetaRelf.setApplicationContext(applicationContext);
        MetaManager.singleInstance().scanAndParse("org.geelato", false);
        // 解析脚本：sql、业务规则
        if (this.getClass().getClassLoader() == null || this.getClass().getClassLoader().getResource("//") == null) {
            initFromFatJar();
        } else {
            initFromExploreFile();
        }
    }

    /**
     * 配置文件不打包在jar包中运行，可基于文件系统加载配置文件
     *
     * @throws IOException
     */
    protected void initFromExploreFile() throws IOException {
        //String path =applicationContext.getEnvironment().getProperty("geelato.res.path").trim();
        String path = this.getClass().getClassLoader().getResource("//").getPath();
        //由测试类启动时，修改资源目录为源码下的资源目录
        path = path.replace("test-classes", "classes");
        //--1、sql
        SqlScriptManagerFactory.get("sql").loadFiles(path + "/geelato/core/sql/");
        //--2、业务规则
        BizManagerFactory.getBizRuleScriptManager("rule").setDao(dao);
        BizManagerFactory.getBizRuleScriptManager("rule").loadFiles(path + "/geelato/core/rule/");
        //--3、创建表结构
        dbGenerateDao.createAllTables(true);
        //--4、初始化表数据
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(getProperty("geelato.init.sql", "/geelato/core/data/init.sql"));
        SqlFiles.loadAndExecute(is, dao.getJdbcTemplate(), isWinOS);
    }

    /**
     * 打包成单个fatJar文件运行时，加载的资源不能采用文件系统加载，需采用流的方式加载
     *
     * @throws IOException
     */
    protected void initFromFatJar() throws IOException {
        //--1、sql
        SqlScriptManagerFactory.get("sql").loadResource("/geelato/core/sql/**/*.sql");
        //--2、业务规则
        BizManagerFactory.getBizRuleScriptManager("rule").setDao(dao);
        BizManagerFactory.getBizRuleScriptManager("rule").loadResource("/geelato/core/rule/**/*.js");
        //--3、创建表结构
        dbGenerateDao.createAllTables(true);
        //--4、初始化表数据
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String initSql = "/geelato/core/data/*.sql";
        try {
            Resource[] resources = resolver.getResources("/geelato/core/data/*.sql");
            for (Resource resource : resources) {
                InputStream is = resource.getInputStream();
                SqlFiles.loadAndExecute(is, dao.getJdbcTemplate(), isWinOS);
            }
        } catch (IOException e) {
            logger.error("加载、初始化数据（" + initSql + "）失败。", e);
        }
    }

    protected String getProperty(String key, String defaultValue) {
        String value = applicationContext.getEnvironment().getProperty(key);
        return value == null ? defaultValue : value;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        dbGenerateDao.setDao(dao);
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
