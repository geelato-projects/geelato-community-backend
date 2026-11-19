package cn.geelato.web.platform.srv.weixin.miniapp.spi;

import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppLoginResult;

/**
 * 登录结果增强 SPI：login/bind 成功组装平台标准 LoginResult 后调用，宿主应用可填充
 * 平台不感知的业务字段（如角色 roleCodes/roleIds/roles、客户名 cooperatingOrgName/orgCode）。
 * <p>
 * 平台默认实现为空操作；宿主定义同类型 Bean 即覆盖（SecurityProviderConfiguration 的
 * ConditionalOnMissingBean 装配惯例）。实现方应自行容错：增强失败不应阻断登录，
 * 建议内部 try/catch 仅记日志。
 */
public interface MiniAppLoginResultEnhancer {

    /**
     * @param result 已含平台标准字段与 token 的登录结果（可直接修改）
     * @param user   本次登录的平台用户实体
     */
    void enhance(MiniAppLoginResult result, User user);
}
