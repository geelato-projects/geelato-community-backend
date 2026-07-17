package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import cn.geelato.meta.User;
import cn.geelato.web.platform.cache.SafeJ2CacheSupport;
import cn.geelato.web.platform.utils.EncryptUtil;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
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
        HashMap<String,Object> userConfig = new HashMap<>();
        CacheChannel cache = SafeJ2CacheSupport.getChannel();
        if (cache != null) {
            CacheObject userConfigCacheObject = cache.get("config", user.getId(), null);
            if (userConfigCacheObject.getValue() != null) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) userConfigCacheObject.getValue();
                list.forEach((item) -> userConfig.put(item.get("code").toString(), item));
            }
        }
        map.put("userConfig", userConfig);

        HashMap<String,Object> commonConfig = new HashMap<>();
        if (cache != null) {
            CacheObject commonConfigCacheObject = cache.get("config", user.getId().toString(), null);
            if (commonConfigCacheObject.getValue() != null) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) commonConfigCacheObject.getValue();
                list.forEach((item) -> commonConfig.put(item.get("code").toString(), item));
            }
        }
        map.put("commonConfig", commonConfig);

        List<Map<String, Object>> moduleList = dao.queryForMapList(Module.class);
        for (Map module : moduleList) {
            long id = Long.parseLong(module.get("id").toString());
            List<Map> menuItemList = ruleService.queryForTree("platform_menu_item", id, "items");
            module.put("tree", menuItemList);
        }
        map.put("modules", moduleList);

        return map;
    }

}
