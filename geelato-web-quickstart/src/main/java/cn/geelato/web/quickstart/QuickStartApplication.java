package cn.geelato.web.quickstart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableConfigurationProperties
@EnableCaching
@EnableAsync(proxyTargetClass = true)
@Slf4j
@SpringBootApplication(scanBasePackages = {"cn.geelato"})
public class QuickStartApplication {

    public static void main(String[] args) {
        log.info("Starting QuickStartApplication");
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
