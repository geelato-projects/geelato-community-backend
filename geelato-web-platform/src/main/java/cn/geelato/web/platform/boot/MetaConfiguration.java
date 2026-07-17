package cn.geelato.web.platform.boot;

import cn.geelato.core.meta.ConflictStrategy;
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
        // 读取冲突检测总开关：geelato.meta.conflict-detect.enabled，默认关闭（false）。
        // 关闭时所有冲突检测/合并改动均不生效，保持原有运行行为；开启时才启用冲突告警与合并策略。
        boolean conflictDetectEnabled = Boolean.parseBoolean(getProperty("geelato.meta.conflict-detect.enabled", "false"));
        metaManager.setConflictDetectEnabled(conflictDetectEnabled);
        // 冲突合并策略：geelato.meta.conflict-strategy = DATABASE | CLASS，默认 CLASS（仅在总开关开启时生效）
        String strategy = getProperty("geelato.meta.conflict-strategy", ConflictStrategy.CLASS.name());
        try {
            metaManager.setConflictStrategy(ConflictStrategy.valueOf(strategy.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            metaManager.setConflictStrategy(ConflictStrategy.CLASS);
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
