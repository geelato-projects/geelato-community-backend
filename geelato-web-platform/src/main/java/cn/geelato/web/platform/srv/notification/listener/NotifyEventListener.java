package cn.geelato.web.platform.srv.notification.listener;

import cn.geelato.web.platform.event.NotifyEvent;
import cn.geelato.web.platform.srv.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 通知事件监听器：接收 {@link NotifyEvent}，委托 {@link NotificationService#dispatch} 编排投递。
 * <p>
 * 异步执行，业务方 publish 事件后立即返回，不阻塞业务事务（dispatch 内部自带事务）。
 *
 * @author geelato
 */
@Component
@Slf4j
public class NotifyEventListener {

    private final NotificationService notificationService;

    @Autowired
    public NotifyEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("eventExecutor")
    @EventListener
    public void onNotify(NotifyEvent event) {
        try {
            notificationService.dispatch(event.getRequest());
        } catch (Exception e) {
            // 事件投递失败不影响业务；站内信失败可由调用方重试或人工补救
            log.error("处理通知事件失败：{}", e.getMessage(), e);
        }
    }
}
