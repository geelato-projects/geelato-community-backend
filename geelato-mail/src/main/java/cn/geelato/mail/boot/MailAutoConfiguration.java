package cn.geelato.mail.boot;

import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.orm.config.OrmDynamicDataSourceAutoConfiguration;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.util.unit.DataSize;

/**
 * 邮件模块自动装配（Boot3 {@code AutoConfiguration.imports} 注册）。
 *
 * <p>模块默认作为脚手架（geelato-app-scaffold-starter）的一部分生效，无模块级开关；
 * 不需要邮件能力的应用在 pom 中对依赖做 exclusion 排除。业务组件（controller/service）
 * 由宿主的 {@code @ComponentScan("cn.geelato")}（BootApplication）发现，本类只装配
 * 基础设施 Bean，且均带 {@code @ConditionalOnMissingBean} 让位宿主自定义。</p>
 */
@AutoConfiguration
@AutoConfigureAfter(OrmDynamicDataSourceAutoConfiguration.class)
@EnableConfigurationProperties(MailProperties.class)
public class MailAutoConfiguration {

    /**
     * 邮件附件上传 multipart 限制（25MB/30MB 全局上限兜底，业务级 20MB 校验在
     * MailAttachmentStorageService 逐文件执行）。宿主自定义 MultipartConfigElement 时让位。
     */
    @Bean
    @ConditionalOnMissingBean(MultipartConfigElement.class)
    public MultipartConfigElement mailMultipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(25));
        factory.setMaxRequestSize(DataSize.ofMegabytes(30));
        return factory.createMultipartConfig();
    }

    /**
     * 启动时幂等建表（geelato.mail.auto-init-tables，默认 true）。
     * 表建在邮件实体实际路由到的数据源（@Entity(catalog="mail") + catalog-mapping/dev_table
     * 登记解析，缺省主库），须在 ORM catalog 映射注入之后执行（@AutoConfigureAfter）。
     */
    @Bean
    @ConditionalOnBean({EntityDataSourceResolver.class, DynamicDataSourceRegistry.class})
    @ConditionalOnMissingBean(MailSchemaInitializer.class)
    @ConditionalOnProperty(prefix = "geelato.mail", name = "auto-init-tables", havingValue = "true", matchIfMissing = true)
    public MailSchemaInitializer mailSchemaInitializer(EntityDataSourceResolver entityDataSourceResolver,
                                                       DynamicDataSourceRegistry dynamicDataSourceRegistry) {
        return new MailSchemaInitializer(entityDataSourceResolver, dynamicDataSourceRegistry);
    }
}
