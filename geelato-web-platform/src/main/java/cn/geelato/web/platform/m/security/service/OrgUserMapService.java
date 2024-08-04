package cn.geelato.web.platform.m.security.service;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.web.platform.enums.IsDefaultOrgEnum;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.security.entity.Org;
import cn.geelato.web.platform.m.security.entity.OrgUserMap;
import cn.geelato.web.platform.m.security.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class OrgUserMapService extends BaseService {
    @Autowired
    private UserService userService;
    @Autowired
    private OrgService orgService;

    /**
     * 根据组织ID和用户ID查询组织用户映射关系列表
     *
     * @param orgId  组织ID
     * @param userId 用户ID
     * @return 组织用户映射关系列表
     */
    public List<OrgUserMap> queryModelByIds(String orgId, String userId) {
        List<OrgUserMap> list = new ArrayList<>();
        if (Strings.isNotBlank(orgId) && Strings.isNotBlank(userId)) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("orgId", FilterGroup.Operator.in, orgId);
            filter.addFilter("userId", FilterGroup.Operator.in, userId);
            list = this.queryModel(OrgUserMap.class, filter);
        }

        return list;
    }

    /**
     * 批量插入组织用户映射关系
     *
     * @param model 组织用户映射关系对象
     * @return 插入的组织用户映射关系列表
     * @throws RuntimeException 当组织或用户信息为空时抛出异常
     */
    public List<OrgUserMap> insertModels(OrgUserMap model) {
        // 角色存在，
        List<Org> orgs = orgService.getModelsById(Org.class, model.getOrgId());
        if (orgs == null || orgs.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 用户信息，
        List<User> users = userService.getModelsById(User.class, model.getUserId());
        if (users == null || users.size() == 0) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色用户信息，
        List<OrgUserMap> maps = this.queryModelByIds(model.getOrgId(), model.getUserId());
        // 对比插入
        List<OrgUserMap> list = new ArrayList<>();
        for (Org org : orgs) {
            for (User user : users) {
                boolean isExist = false;
                if (maps != null && maps.size() > 0) {
                    for (OrgUserMap map : maps) {
                        if (org.getId().equals(map.getOrgId()) && user.getId().equals(map.getUserId())) {
                            isExist = true;
                            break;
                        }
                    }
                }
                if (!isExist) {
                    OrgUserMap map = new OrgUserMap();
                    map.setOrgId(org.getId());
                    map.setOrgName(org.getName());
                    map.setUserId(user.getId());
                    map.setUserName(user.getName());
                    map.setDefaultOrg(org.getId().equals(user.getOrgId()) ? IsDefaultOrgEnum.IS.getCode() : IsDefaultOrgEnum.NO.getCode());
                    map = this.createModel(map);
                    list.add(map);
                }
            }
        }

        return list;
    }


    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(OrgUserMap model) {
        isDeleteOrgUserMap(model);
        // 清理 用户默认部门
        Map<String, Object> params = new HashMap<>();
        params.put("orgId", model.getOrgId());
        params.put("id", model.getUserId());
        List<User> uList = userService.queryModel(User.class, params);
        if (uList != null) {
            for (User uModel : uList) {
                uModel.setOrgId(null);
                uModel.setOrgName(null);
                userService.updateModel(uModel);
            }
        }
    }

    /**
     * 基础逻辑删除
     *
     * @param model
     */
    public void isDeleteOrgUserMap(OrgUserMap model) {
        model.setDefaultOrg(IsDefaultOrgEnum.NO.getCode());
        super.isDeleteModel(model);
    }
}
