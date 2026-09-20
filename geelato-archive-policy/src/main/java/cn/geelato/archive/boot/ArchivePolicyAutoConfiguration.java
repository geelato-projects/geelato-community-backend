package cn.geelato.archive.boot;

import cn.geelato.archive.config.ArchiveProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 归档策略管理自动装配：仅注册全局配置。
 * <p>业务组件（service/controller，@Component 注解）由宿主的
 * {@code @ComponentScan("cn.geelato")}（BootApplication）发现——对齐 geelato-mail 模式；
 * 本类保证 {@link ArchiveProperties} Bean 就绪。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ArchiveProperties.class)
public class ArchivePolicyAutoConfiguration {
}
