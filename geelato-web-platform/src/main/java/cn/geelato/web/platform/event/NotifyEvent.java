package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.srv.notification.dto.NotifyRequest;

/**
 * 通知发起事件。
 * <p>
 * 业务方零耦合触发通知（站内信及外部渠道）：
 * <pre>
 * EventPublisher.publish(new NotifyEvent(this,
 *     NotifyRequest.of(List.of("u1","u2"), "合同待审批", "请尽快审批")
 *         .setBizType("contract").setBizId("HT-001")
 *         .setActionUrl("/contract/approve?id=HT-001")));
 * </pre>
 * 实际投递由 {@code NotifyEventListener}（专用 @EventListener，异步）调用
 * {@code NotificationService#dispatch} 完成。本事件的 handle() 为空，
 * 因业务逻辑由专用监听器承载（与全局 EventBusListener 解耦，避免重复处理）。
 *
 * @author geelato
 */
public class NotifyEvent extends BusinessEvent {

    private final NotifyRequest request;

    public NotifyEvent(Object source, NotifyRequest request) {
        super(source);
        this.request = request;
    }

    public NotifyRequest getRequest() {
        return request;
    }

    @Override
    public String getEventCode() {
        return "Notify";
    }

    @Override
    public void handle() {
        // 业务逻辑由专用监听器 NotifyEventListener 承载，这里无需实现。
    }
}
