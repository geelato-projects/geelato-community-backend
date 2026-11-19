package cn.geelato.web.platform.srv.weixin.miniapp.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 手机号解析出的账号（{@code MiniAppAccountResolver} SPI 的返回载体）。
 * <p>
 * 含原始字段；pre-bind 响应中的脱敏展示由平台 LoginService 统一完成（见 MiniAppPreBindAccount）。
 */
@Setter
@Getter
public class MiniAppAccount {

    /** 平台用户 ID（platform_user.id） */
    private String userId;

    private String loginName;

    private String userName;

    /** 手机号（原始值，平台负责脱敏后返回前端） */
    private String mobile;

    /** 客户（合作单位）ID，供 enhancer 回查客户名/编码 */
    private String cooperatingOrgId;

    /** 客户（合作单位）名称（展示用，由 resolver 实现方填充；平台默认实现无客户表时为 null） */
    private String orgName;
}
