package cn.geelato.mail;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 邮件模块测试引导类（仅测试作用域）。
 *
 * 只扫描邮件模块自身包；ORM/数据源等基础设施由 geelato-orm 的自动装配提供
 * （spring.datasource.primary.* 属性）。用于 MailGreenMailIntegrationTest 等
 * 需要完整 Spring 上下文的集成测试，脱离宿主应用独立运行。
 */
@SpringBootApplication(scanBasePackages = "cn.geelato.mail")
public class MailTestApplication {
}
