package cn.geelato.web.platform.boot;

import cn.geelato.core.meta.MetaManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetaConfiguration implements ApplicationContextAware {
    protected ApplicationContext applicationContext;
    public MetaConfiguration() {
    }

    private void initClassPackageMeta() {
        String[] packageNames = getProperty("geelato.meta.scan-package-names", "cn.geelato").split(",");
        for (String packageName : packageNames) {
            MetaManager.singleInstance().scanAndParse(packageName, false);
        }
    }

    protected String getProperty(String key, String defaultValue) {
        String value = applicationContext.getEnvironment().getProperty(key);
        return value == null ? defaultValue : value;
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext context) throws BeansException {
        this.applicationContext=context;
        initClassPackageMeta();
    }
}
