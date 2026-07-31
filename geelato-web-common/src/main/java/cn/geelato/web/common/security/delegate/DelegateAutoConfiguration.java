package cn.geelato.web.common.security.delegate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 委托代办会话存储的默认装配。
 * <p>
 * 当容器中不存在任何 {@link DelegateSessionStore} 实现时，提供进程内内存实现作为默认值。
 * 多实例部署可通过自定义一个 {@link DelegateSessionStore} Bean（如 Redis 实现）覆盖默认。
 * <p>
 * 使用 {@code @Configuration + @Bean + @ConditionalOnMissingBean} 而非
 * {@code @Component + @ConditionalOnMissingBean}，确保条件在所有候选 bean 注册后可靠求值。
 *
 * @author geelato
 */
@Configuration
public class DelegateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DelegateSessionStore.class)
    public DelegateSessionStore inMemoryDelegateSessionStore() {
        return new InMemoryDelegateSessionStore();
    }
}
