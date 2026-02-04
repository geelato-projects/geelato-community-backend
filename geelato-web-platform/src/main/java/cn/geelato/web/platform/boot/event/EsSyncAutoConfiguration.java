package cn.geelato.web.platform.boot.event;

import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.web.platform.event.EsSyncSaveListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class EsSyncAutoConfiguration {
    private final EsSyncSaveListener listener;

    public EsSyncAutoConfiguration() {
        this.listener = new EsSyncSaveListener();
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
