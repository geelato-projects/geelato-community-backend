package cn.geelato.web.platform.srv.weixin.miniapp.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * pre-bind 脱敏账号信息（确认弹窗展示用，前端照原文展示、勿再加工）。
 */
@Setter
@Getter
public class MiniAppPreBindAccount {

    private String userId;

    private String loginName;

    /** 姓名（脱敏：保留首字，其余 *） */
    private String userName;

    /** 客户（合作单位）名称 */
    private String orgName;

    /** 手机号（脱敏：138****1234） */
    private String mobile;
}
