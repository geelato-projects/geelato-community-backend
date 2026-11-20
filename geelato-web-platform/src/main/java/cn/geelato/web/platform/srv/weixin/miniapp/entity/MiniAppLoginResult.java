package cn.geelato.web.platform.srv.weixin.miniapp.entity;

import cn.geelato.web.platform.srv.security.entity.LoginResult;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

/**
 * 微信小程序登录/绑定成功返回的用户身份：平台标准 {@link LoginResult}（身份对象内含 token，
 * 与 /api/user/login、/api/oauth2/login 同构）+ 小程序扩展字段。
 * <p>
 * 扩展字段由宿主应用的 {@code MiniAppLoginResultEnhancer} 填充（平台默认实现留空）：
 * cooperatingOrgName/orgCode 取自客户（合作单位）表；expiresIn 由平台填充。
 */
@Setter
@Getter
public class MiniAppLoginResult extends LoginResult {

    /** 客户（合作单位）名称，由 MiniAppLoginResultEnhancer 填充 */
    private String cooperatingOrgName;

    /** 客户（合作单位）编码，由 MiniAppLoginResultEnhancer 填充 */
    private String orgCode;

    /** 会话 token 有效期（秒）。非扩展字段：与 Web 端身份对象既有惯例一致（前端缺失兜底 604800） */
    private Integer expiresIn;

    /** 以平台标准身份为基础构造小程序身份（token 由调用方随后签发覆盖） */
    public static MiniAppLoginResult of(LoginResult base) {
        MiniAppLoginResult result = new MiniAppLoginResult();
        BeanUtils.copyProperties(base, result);
        return result;
    }
}
