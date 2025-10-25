package cn.geelato.web.platform.srv.security.wechat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WeChatError {
    private Integer errcode;
    private String errmsg;
}
