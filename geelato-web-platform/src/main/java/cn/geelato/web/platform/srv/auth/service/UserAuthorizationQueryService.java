package cn.geelato.web.platform.srv.auth.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.auth.AuthBadRequestException;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class UserAuthorizationQueryService {
    private final Dao dao;

    public UserAuthorizationQueryService(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    public List<Map<String, Object>> getCurrentUserMenu(User user, String token, String flag, String appId, String tenantCode) {
        if (user == null || Strings.isBlank(token)) {
            throw new AuthBadRequestException("User or token is null");
        }
        if (Strings.isNotBlank(tenantCode) && !tenantCode.equalsIgnoreCase(user.getTenantCode())) {
            throw new AuthBadRequestException("user tenant code not equal");
        }
        String effectiveTenantCode = user.getTenantCode();
        if (Strings.isBlank(appId) || Strings.isBlank(effectiveTenantCode)) {
            return new ArrayList<>();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("currentUser", user.getId());
        map.put("appId", appId);
        map.put("tenantCode", effectiveTenantCode);
        map.put("flag", flag);
        return dao.queryForMapList("select_platform_tree_node_app_page", map);
    }
}
