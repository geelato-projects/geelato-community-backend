package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.base.entity.AppRestfulMap;
import cn.geelato.web.platform.m.base.entity.CustomRestful;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class RestfulService extends BaseService {
    @Lazy
    @Autowired
    private AppRestfulMapService appRestfulMapService;

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(CustomRestful model) {
        // 删除关联申请
        Map<String, Object> params = new HashMap<>();
        params.put("restfulId", model.getId());
        List<AppRestfulMap> list = appRestfulMapService.queryModel(AppRestfulMap.class, params);
        if (list != null && list.size() > 0) {
            for (AppRestfulMap map : list) {
                appRestfulMapService.isDeleteModel(map);
            }
        }
        // 删除
        super.isDeleteModel(model);
    }
}
