package cn.geelato.web.platform.boot.event;

import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.web.platform.event.EsSyncSaveListener;
import cn.geelato.web.platform.event.EsSyncService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import javax.annotation.PostConstruct;

@Configuration
public class EsSyncAutoConfiguration {
    private final EsSyncSaveListener listener;

    public EsSyncAutoConfiguration(ObjectProvider<EsSyncService> esSyncServiceProvider) {
        this.listener = new EsSyncSaveListener(esSyncServiceProvider.getIfAvailable());
    }

    @Bean
    public EsSyncSaveListener esSyncSaveListener() {
        return listener;
    }

    @PostConstruct
    public void register() {
        SaveEventManager.registerAfterIfAbsent(listener);
    }
}
