package cn.geelato.web.platform.boot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@Slf4j
public class LogbackControllerCallRecorder implements ControllerCallRecorder {
    @Resource
    private ObjectMapper objectMapper;
    @Override
    public void record(ControllerInvocationLog logInfo) {
        try {
            String json = objectMapper.writeValueAsString(logInfo);
            log.info(json);
        } catch (Exception e) {
            log.error("record_failed", e);
        }
    }
}
