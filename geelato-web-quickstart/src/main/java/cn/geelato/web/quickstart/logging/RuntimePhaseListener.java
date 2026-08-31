package cn.geelato.web.quickstart.logging;

import cn.geelato.logging.logback.StartupPhaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 监听 Spring Boot 的 {@link ApplicationReadyEvent}，在应用完全就绪
 * （CommandLineRunner 执行完毕、HTTP 端口已就绪）后，将日志阶段从"启动期"切换为"运行期"。
 *
 * <p>切换后：启动日志文件不再接收日志，运行错误日志文件开始按 WARN+ 级别接收。</p>
 */
@Component
public class RuntimePhaseListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RuntimePhaseListener.class);

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        StartupPhaseManager.markRuntimeStarted();
        logger.info("[geelato] 应用已就绪，日志切换为运行期：startup.log 停止写入，runtime.log 开始记录 WARN 及以上级别。");
    }
}
