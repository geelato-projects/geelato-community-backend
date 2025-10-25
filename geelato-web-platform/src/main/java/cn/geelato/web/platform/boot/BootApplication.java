package cn.geelato.web.platform.boot;

import cn.geelato.core.biz.rules.BizManagerFactory;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.graal.GraalManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.run.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.util.Objects;

@ComponentScan(basePackages = {"cn.geelato"})
@Slf4j
public class BootApplication implements CommandLineRunner {

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;

    @Override
    public void run(String... args) throws Exception {
        log.info("[start args]：{}", StringUtils.join(args, ","));
        log.info("[configuration file]：{}", applicationContext.getEnvironment().getProperty("geelato.env"));
        log.info("[start application]...start");
        resolveSqlScript(args);
        resolveGraalContext();
        initEnvironment();
        DataSourceManager.singleInstance().parseDataSourceMeta(this.dao);
        log.info("[start application]...finish");
        log.info("[application version is {}:{}]", Version.current.getEdition(), Version.current.getVersion());
    }

    private void resolveGraalContext() {
        String[] packageNames = getProperty("geelato.graal.scan-package-names", "cn.geelato").split(",");
        for (String packageName : packageNames) {
            GraalManager.singleInstance().initGraalService(packageName);
            GraalManager.singleInstance().initGraalVariable(packageName);
        }
    }


    private void resolveSqlScript(String... args) throws IOException {
        if (this.getClass().getClassLoader() == null || this.getClass().getClassLoader().getResource("//") == null) {
            initFromFatJar();
        } else {
            initFromExploreFile(args);
        }
    }

    public void initEnvironment(){
        EnvManager.singleInstance().setJdbcTemplate(dao.getJdbcTemplate());
        EnvManager.singleInstance().EnvInit();
    }
    /**
     * 配置文件不打包在jar包中运行，可基于文件系统加载配置文件
     *
     */
    protected void initFromExploreFile(String... args) throws IOException {

        String path = Objects.requireNonNull(this.getClass().getClassLoader().getResource("//")).getPath();
        path = path.replace("test-classes", "classes");
        SqlScriptManagerFactory.get("sql").loadFiles(path + "/geelato/web/platform/sql/");
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
    protected void initFromFatJar() {
        SqlScriptManagerFactory.get("sql").loadResource("/geelato/web/platform/sql/**/*.sql");
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
