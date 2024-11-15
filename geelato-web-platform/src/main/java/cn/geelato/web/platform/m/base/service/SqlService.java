package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.base.entity.AppSqlMap;
import cn.geelato.web.platform.m.base.entity.CustomSql;
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
public class SqlService extends BaseService {
    @Lazy
    @Autowired
    private AppSqlMapService appSqlMapService;

    /**
     * 逻辑删除
     * <p>
     * 该方法用于逻辑删除指定的CustomSql模型对象。
     * 在删除前，会先删除与该CustomSql对象相关联的申请记录。
     *
     * @param model 要逻辑删除的CustomSql模型对象
     */
    public void isDeleteModel(CustomSql model) {
        // 删除关联申请
        Map<String, Object> params = new HashMap<>();
        params.put("sqlId", model.getId());
        List<AppSqlMap> list = appSqlMapService.queryModel(AppSqlMap.class, params);
        if (list != null && !list.isEmpty()) {
            for (AppSqlMap map : list) {
                appSqlMapService.isDeleteModel(map);
            }
        }
        // 删除
        super.isDeleteModel(model);
    }
}
