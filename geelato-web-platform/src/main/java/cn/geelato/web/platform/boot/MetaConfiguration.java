package cn.geelato.web.platform.boot;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.spi.MetaBootstrap;
import cn.geelato.core.meta.spi.MetaResourceProvider;
import cn.geelato.core.meta.spi.MetaStore;
import cn.geelato.core.orm.Dao;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetaConfiguration extends BaseConfiguration{
    @Resource
    @Qualifier("primaryDao")
    private Dao dao;
    @Autowired(required = false)
    private MetaStore metaStore;
    @Autowired(required = false)
    private MetaResourceProvider metaResourceProvider;
    @Autowired(required = false)
    private MetaBootstrap metaBootstrap;
    MetaManager metaManager=MetaManager.singleInstance();
    public MetaConfiguration() {

    }
    @Override
    public void setApplicationContext(@NotNull ApplicationContext context) throws BeansException {
        this.applicationContext=context;
        if (metaStore != null) {
            metaManager.setMetaStore(metaStore);
        }
        if (metaResourceProvider != null) {
            metaManager.setMetaResourceProvider(metaResourceProvider);
        }
        initClassPackageMeta();
        initDataBaseMeta();
    }
    private void initClassPackageMeta() {
        String[] packageNames = getProperty("geelato.meta.scan-package-names", "cn.geelato").split(",");
        for (String packageName : packageNames) {
            metaManager.scanAndParse(packageName, false);
        }
    }

    private void initDataBaseMeta() {
        metaManager.parseDBMeta(dao);
        if (metaBootstrap != null) {
            metaBootstrap.bootstrap(metaManager, dao);
        }
    }

}
