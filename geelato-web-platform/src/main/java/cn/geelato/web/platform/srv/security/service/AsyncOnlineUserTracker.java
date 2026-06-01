package cn.geelato.web.platform.srv.security.service;

import cn.geelato.security.User;
import cn.geelato.web.common.online.OnlineUserTracker;
import cn.geelato.web.common.traffic.TrafficTagContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
@Primary
@Slf4j
public class AsyncOnlineUserTracker implements OnlineUserTracker {
    private final RedisOnlineUserTracker redisOnlineUserTracker;
    private final Executor eventExecutor;

    @Autowired
    public AsyncOnlineUserTracker(RedisOnlineUserTracker redisOnlineUserTracker,
                                  @Qualifier("onlineUserExecutor") Executor eventExecutor) {
        this.redisOnlineUserTracker = redisOnlineUserTracker;
        this.eventExecutor = eventExecutor;
    }

    @Override
    public void touch(User user, HttpServletRequest request) {
        String trafficTag = null;
        try {
            trafficTag = TrafficTagContext.get();
        } catch (Exception ignored) {
        }
        try {
            String finalTrafficTag = trafficTag;
            eventExecutor.execute(() -> {
                try {
                    redisOnlineUserTracker.touch(user, finalTrafficTag);
                } catch (Exception ex) {
                    log.debug("touch online user failed", ex);
                }
            });
        } catch (Exception ex) {
            log.debug("submit touch online user task failed", ex);
        }
    }
}
