package cn.geelato.web.platform.boot;

import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.web.platform.event.EsSyncSaveListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * ES 实体保存同步监听装配。elasticsearch-java 为 optional 依赖，classpath 无 ES 客户端时不注册
 * （监听器内部另有 ES_SYNC_ENABLED 总闸，当前恒关）。
 */
@Configuration
@ConditionalOnClass(name = "co.elastic.clients.elasticsearch.ElasticsearchClient")
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

    @PreDestroy
    public void unregister() {
        // B2：容器销毁时注销监听器，防热部署泄漏
        SaveEventManager.unregisterAfter(listener);
    }
}
