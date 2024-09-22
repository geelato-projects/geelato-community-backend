package cn.geelato.web.platform.boot;

import cn.geelato.core.biz.rules.BizManagerFactory;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.graal.GraalManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.MetaReflex;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.DbGenerateDao;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.util.Properties;

@ComponentScan(basePackages = {"cn.geelato"})
@Slf4j
public class BootApplication implements CommandLineRunner {
    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;

    @Autowired
    protected DbGenerateDao dbGenerateDao;

    protected boolean isWinOS;
    protected boolean isIgnoreInitData = false;

    private void assertOS() {
        Properties prop = System.getProperties();
        String os = prop.getProperty("os.name");
        log.info("[operate system]{}", os);
        isWinOS = os.toLowerCase().startsWith("win");
    }

    /**
     * @param args 每一个参数
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("[start args]：{}", StringUtils.join(args, ","));
        log.info("[configuration file]：{}", applicationContext.getEnvironment().getProperty("geelato.env"));
        log.info("[start application]...start");
        assertOS();
        parseStartArgs(args);
        resolveSqlScript(args);
        resolveGraalContext();
        initEnvironment();
        log.info("[start application]...finish");
    }

    private void resolveGraalContext() {
        String[] packageNames = getProperty("geelato.graal.scan-package-names", "cn.geelato").split(",");
        for (String packageName : packageNames) {
            GraalManager.singleInstance().initGraalService(packageName);
            GraalManager.singleInstance().initGraalVariable(packageName);
        }
    }





    private void parseStartArgs(String... args) {
        isIgnoreInitData = false;
    }


    public void initMeta() throws IOException {
        MetaReflex.setApplicationContext(applicationContext);
        initClassPackageMeta();
        initDataBaseMeta();
    }



    private void initClassPackageMeta() {
        String[] packageNames = getProperty("geelato.meta.scan-package-names", "cn.geelato").split(",");
        for (String packageName : packageNames) {
            MetaManager.singleInstance().scanAndParse(packageName, false);
        }
    }
    private void initDataBaseMeta() {
        MetaManager.singleInstance().parseDBMeta(dao);
    }
    private void resolveSqlScript(String... args) throws IOException {
        if (this.getClass().getClassLoader() == null || this.getClass().getClassLoader().getResource("//") == null) {
            initFromFatJar();
        } else {
            initFromExploreFile(args);
        }
    }

    public void initEnvironment(){
        EnvManager.singleInstance().SetDao(dao);
        EnvManager.singleInstance().EnvInit();
    }
    /**
     * 配置文件不打包在jar包中运行，可基于文件系统加载配置文件
     *
     */
    protected void initFromExploreFile(String... args) throws IOException {

        String path = this.getClass().getClassLoader().getResource("//").getPath();
        //由测试类启动时，修改资源目录为源码下的资源目录
        path = path.replace("test-classes", "classes");
        //--1、sql
        SqlScriptManagerFactory.get("sql").loadFiles(path + "/geelato/web/platform/sql/");
        //--2、业务规则
        BizManagerFactory.getBizRuleScriptManager("rule").setDao(dao);
        BizManagerFactory.getBizRuleScriptManager("rule").loadFiles(path + "/geelato/web/platform/rule/");
        BizManagerFactory.getBizMvelRuleManager("mvelRule").setDao(dao);
        BizManagerFactory.getBizMvelRuleManager("mvelRule").loadDb();

        DbScriptManagerFactory.get("db").setDao(dao);
        DbScriptManagerFactory.get("db").loadDb();
    }

    /**
     * 打包成单个fatJar文件运行时，加载的资源不能采用文件系统加载，需采用流的方式加载
     *
     */
    protected void initFromFatJar() throws IOException {
        //--1、sql
        SqlScriptManagerFactory.get("sql").loadResource("/geelato/web/platform/sql/**/*.sql");
        //--2、业务规则
        BizManagerFactory.getBizRuleScriptManager("rule").setDao(dao);
        BizManagerFactory.getBizRuleScriptManager("rule").loadResource("/geelato/web/platform/rule/**/*.js");
        BizManagerFactory.getBizMvelRuleManager("mvelRule").setDao(dao);
        BizManagerFactory.getBizMvelRuleManager("mvelRule").loadDb();

        DbScriptManagerFactory.get("db").setDao(dao);
        DbScriptManagerFactory.get("db").loadDb();
    }

    protected String getProperty(String key, String defaultValue) {
        String value = applicationContext.getEnvironment().getProperty(key);
        return value == null ? defaultValue : value;
    }


}
