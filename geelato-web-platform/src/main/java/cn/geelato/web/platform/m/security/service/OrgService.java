package cn.geelato.web.platform.m.security.service;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.web.platform.m.security.enums.OrgTypeEnum;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import cn.geelato.web.platform.m.security.entity.Org;
import cn.geelato.web.platform.m.security.entity.OrgUserMap;
import cn.geelato.web.platform.m.security.entity.User;
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
public class OrgService extends BaseSortableService {
    @Lazy
    @Autowired
    private OrgUserMapService orgUserMapService;
    @Autowired
    private UserService userService;

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(Org model) {
        // 组织删除
        super.isDeleteModel(model);
        // 清理 组织用户表
        Map<String, Object> params = new HashMap<>();
        params.put("orgId", model.getId());
        List<OrgUserMap> oList = orgUserMapService.queryModel(OrgUserMap.class, params);
        if (oList != null) {
            for (OrgUserMap oModel : oList) {
                orgUserMapService.isDeleteOrgUserMap(oModel);
            }
        }
        // 清理 用户表
        List<User> uList = userService.queryModel(User.class, params);
        if (oList != null) {
            for (User uModel : uList) {
                uModel.setOrgId(null);
                uModel.setOrgName(null);
                dao.save(uModel);
            }
        }
    }

    /**
     * 全量查询
     *
     * @param params 条件参数
     * @return
     */
    public List<Map<String, Object>> queryTree(Map<String, Object> params) {
        return dao.queryForMapList("query_tree_platform_org", params);
    }

    /**
     * 获取组织所属公司
     *
     * @param id
     * @return
     */
    public Org getCompany(String id) {
        if (Strings.isNotBlank(id)) {
            Org model = this.getModel(Org.class, id);
            if (model != null) {
                if (OrgTypeEnum.ROOT.getValue().equals(model.getType())) {
                    return model;
                } else if (OrgTypeEnum.COMPANY.getValue().equals(model.getType())) {
                    if (Strings.isNotBlank(model.getPid())) {
                        return getCompany(model.getPid());
                    } else {
                        return model;
                    }
                } else if (Strings.isNotBlank(model.getPid())) {
                    return getCompany(model.getPid());
                }
            }
        }
        return null;
    }
}