package cn.geelato.web.quickstart;

import cn.geelato.web.platform.boot.BootApplication;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties
@MapperScan("cn.geelato.workflow.*.mapper")
@EnableCaching
@EnableAsync
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
