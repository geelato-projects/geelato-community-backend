package cn.geelato.web.quickstart;

import cn.geelato.auth.server.AuthServerConfiguration;
import cn.geelato.web.platform.boot.BootApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties
@Import(AuthServerConfiguration.class)
@ComponentScan(basePackages = {"cn.geelato"})
@EnableCaching
@Slf4j
public class QuickStartApplication extends BootApplication {

    @Override
    public void run(String... strings) throws Exception {
        log.info("QuickStartApplication>run");
        super.run(strings);
    }

    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
