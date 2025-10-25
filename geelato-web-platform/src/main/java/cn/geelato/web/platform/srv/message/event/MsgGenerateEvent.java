package cn.geelato.web.platform.srv.message.event;

import cn.geelato.message.model.PlatformMsg;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MsgGenerateEvent extends ApplicationEvent {

    private final PlatformMsg platformMsg;

    public MsgGenerateEvent(Object source, PlatformMsg platformMsg) {
        super(source);
        this.platformMsg = platformMsg;
    }

}
