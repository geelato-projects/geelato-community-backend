package cn.geelato.web.platform.message.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MsgReceiver {
    private String type;
    private List<String> list;
}
