package cn.geelato.web.platform.srv.weixin.miniapp.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * pre-bind 响应结果。
 */
@Setter
@Getter
public class MiniAppPreBindResult {

    /** 手机号是否匹配到已开通账号；false 时其余字段为 null（业务态，非错误） */
    private boolean matched;

    /** 脱敏账号信息（matched=true 时非空） */
    private MiniAppPreBindAccount account;

    /** 绑定确认票据（matched=true 时非空；5 分钟内有效、一次性，前端原样回传 bind） */
    private String preBindTicket;
}
