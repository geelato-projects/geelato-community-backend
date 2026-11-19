package cn.geelato.web.platform.srv.weixin.miniapp.entity;

import lombok.Getter;

/**
 * 微信绑定成功事件：平台在 bind 成功后发布，宿主应用 @EventListener 自行处理（如审计埋点）。
 * 字段均已在平台侧脱敏，监听方不要期望拿到原始手机号。
 */
@Getter
public class MiniAppBoundEvent {

    private final String userId;
    private final String loginName;
    /** unionid 尾部 6 位（脱敏标识） */
    private final String unionIdTail;
    /** 脱敏手机号（绑定来源，形如 138****1234） */
    private final String mobileMasked;

    public MiniAppBoundEvent(String userId, String loginName, String unionIdTail, String mobileMasked) {
        this.userId = userId;
        this.loginName = loginName;
        this.unionIdTail = unionIdTail;
        this.mobileMasked = mobileMasked;
    }
}
