package cn.geelato.web.runtime.quickstart;

import cn.geelato.web.platform.boot.BootApplication;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"cn.geelato"})
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
