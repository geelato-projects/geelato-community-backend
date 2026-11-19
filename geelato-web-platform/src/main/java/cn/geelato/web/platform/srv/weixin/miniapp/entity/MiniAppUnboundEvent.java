package cn.geelato.web.platform.srv.weixin.miniapp.entity;

import lombok.Getter;

/**
 * 微信解绑成功事件：平台在 unbind 成功后发布，宿主应用 @EventListener 自行处理（如审计埋点）。
 */
@Getter
public class MiniAppUnboundEvent {

    private final String userId;
    private final String loginName;
    /** unionid 尾部 6 位（脱敏标识） */
    private final String unionIdTail;

    public MiniAppUnboundEvent(String userId, String loginName, String unionIdTail) {
        this.userId = userId;
        this.loginName = loginName;
        this.unionIdTail = unionIdTail;
    }
}
