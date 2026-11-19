package cn.geelato.web.platform.srv.weixin.miniapp.spi;

import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.security.service.UserService;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppAccount;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台默认账号解析：按 {@code platform_user.mobile_phone} 精确匹配启用中账号，
 * 多条命中取 create_at 最早一条。del_status 由 UserService 查询基线自动过滤。
 * <p>
 * 宿主可覆盖为按业务侧"已开通账号"表匹配（定义同类型 Bean 即生效）。
 */
@Component
public class DefaultMiniAppAccountResolver implements MiniAppAccountResolver {

    private static final int ENABLE_STATUS_ENABLED = 1;

    private final UserService userService;

    public DefaultMiniAppAccountResolver(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Optional<MiniAppAccount> resolve(String phone) {
        List<User> users = userService.queryModel(User.class, Map.of("mobilePhone", phone));
        User matched = users.stream()
                .filter(u -> u.getEnableStatus() == ENABLE_STATUS_ENABLED)
                .min(Comparator.comparing(User::getCreateAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (matched == null) {
            return Optional.empty();
        }
        MiniAppAccount account = new MiniAppAccount();
        account.setUserId(matched.getId());
        account.setLoginName(matched.getLoginName());
        account.setUserName(matched.getName());
        account.setMobile(matched.getMobilePhone());
        account.setCooperatingOrgId(matched.getCooperatingOrgId());
        return Optional.of(account);
    }
}
