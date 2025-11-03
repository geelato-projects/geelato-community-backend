package cn.geelato.web.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommonEventListener implements EnhancedEventListener<CommonEvent<?>> {
    @Override
    public void onEvent(CommonEvent<?> event) {
//        try {
//            // 从事件中获取枚举，执行其处理逻辑
//            event.getEventEnum().handle(event.getData());
//            log.info("事件[{}]处理成功", event.getEventEnum().getEventCode());
//        } catch (Exception e) {
//            log.error("事件[{}]处理失败", event.getEventEnum().getEventCode(), e);
//        }
    }

    @Override
    public Class<CommonEvent<?>> getEventType() {
        return null;
    }

}
