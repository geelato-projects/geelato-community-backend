package cn.geelato.web.platform.boot;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import com.itextpdf.text.Meta;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;

@Configuration
public class MetaConfiguration extends BaseConfiguration{
    @Resource
    @Qualifier("primaryDao")
    private Dao dao;
    MetaManager metaManager=MetaManager.singleInstance();
    public MetaConfiguration() {

    }
    @Override
    public void setApplicationContext(@NotNull ApplicationContext context) throws BeansException {
        this.applicationContext=context;
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
    }
}
