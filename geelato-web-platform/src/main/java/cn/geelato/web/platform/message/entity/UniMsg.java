package cn.geelato.web.platform.message.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UniMsg {
    private String title;
    private String content;
    private String sender;
    private MsgReceiver receiver;
    private String type;
    private String buss;
}
