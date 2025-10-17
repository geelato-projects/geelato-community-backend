package cn.geelato.test.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Geelato测试自动配置类
 */
@Configuration
@ComponentScan("cn.geelato.test")
@EnableAspectJAutoProxy
public class GeelatoTestAutoConfiguration {
}