package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.srv.base.service.RuleService;
import cn.geelato.meta.User;
import cn.geelato.web.platform.utils.EncryptUtil;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import net.oschina.j2cache.J2Cache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by hongxueqian on 14-4-12.
 */
@Component
public class AccountService {
    @Autowired
    @Qualifier("primaryDao")
    private Dao dao;
    @Autowired
    protected RuleService ruleService;

    private final CacheChannel cache = J2Cache.getChannel();


    public User findUserByLoginName(String loginName) {
        return dao.queryForObject(User.class, "loginName", loginName);
    }

    public void registerUser(User user) {
        EncryptUtil.encryptPassword(user);
        if (StringUtils.isBlank(user.getName())) {
            user.setName(user.getLoginName());
        }
        dao.save(user);
    }

    public Map<String,Object> wrapUser(User user) {
        HashMap<String,Object> map = new HashMap<>(3);
        map.put("user", user);
        CacheObject userConfigCacheObject = cache.get("config", user.getId(), null);
        HashMap<String,Object> userConfig = new HashMap<>();
        if (userConfigCacheObject.getValue() != null) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) userConfigCacheObject.getValue();
            list.forEach((item) -> {
                userConfig.put(item.get("code").toString(), item);
            });
        }
        map.put("userConfig", userConfig);

        CacheObject commonConfigCacheObject = cache.get("config", user.getId().toString(), null);
        HashMap<String,Object> commonConfig = new HashMap<>();
        if (userConfigCacheObject.getValue() != null) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) commonConfigCacheObject.getValue();
            list.forEach((item) -> {
                commonConfig.put(item.get("code").toString(), item);
            });
        }
        map.put("commonConfig", commonConfig);

        List<Map<String, Object>> moduleList = dao.queryForMapList(Module.class);
        for (Map module : moduleList) {
            long id = Long.parseLong(module.get("id").toString());
            ApiResult<List<Map>> result = ruleService.queryForTree("platform_menu_item", id, "items");
            List<Map> menuItemList = result.getData();
            module.put("tree", menuItemList);
        }
        map.put("modules", moduleList);

        return map;
    }

}
