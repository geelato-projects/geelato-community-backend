package cn.geelato.web.platform.m.base.service;

import cn.geelato.web.platform.m.security.entity.RoleTreeNodeMap;
import cn.geelato.web.platform.m.security.service.RoleTreeNodeMapService;
import cn.geelato.web.platform.m.base.entity.TreeNode;
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
public class TreeNodeService extends BaseSortableService {
    @Lazy
    @Autowired
    private RoleTreeNodeMapService roleTreeNodeMapService;


    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(TreeNode model) {
        // 用户删除
        super.isDeleteModel(model);
        //
        Map<String, Object> params = new HashMap<>();
        params.put("permissionId", model.getId());
        List<RoleTreeNodeMap> rList = roleTreeNodeMapService.queryModel(RoleTreeNodeMap.class, params);
        if (rList != null) {
            for (RoleTreeNodeMap oModel : rList) {
                roleTreeNodeMapService.isDeleteModel(oModel);
            }
        }
    }
}
