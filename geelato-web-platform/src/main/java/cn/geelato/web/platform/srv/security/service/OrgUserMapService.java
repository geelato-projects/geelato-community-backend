package cn.geelato.web.platform.srv.security.service;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.web.platform.srv.security.enums.IsDefaultOrgEnum;
import cn.geelato.web.platform.srv.base.service.BaseService;
import cn.geelato.meta.Org;
import cn.geelato.meta.OrgUserMap;
import cn.geelato.meta.User;
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
        if (orgs == null || orgs.isEmpty()) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 用户信息，
        List<User> users = userService.getModelsById(User.class, model.getUserId());
        if (users == null || users.isEmpty()) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        // 角色用户信息，
        List<OrgUserMap> maps = this.queryModelByIds(model.getOrgId(), model.getUserId());
        // 对比插入
        List<OrgUserMap> list = new ArrayList<>();
        for (Org org : orgs) {
            for (User user : users) {
                boolean isExist = false;
                if (maps != null && !maps.isEmpty()) {
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
                    map.setDefaultOrg(org.getId().equals(user.getOrgId()) ? IsDefaultOrgEnum.IS.getValue() : IsDefaultOrgEnum.NO.getValue());
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
     * 对传入的OrgUserMap对象进行逻辑删除操作，并清理用户默认部门信息。
     *
     * @param model 需要进行逻辑删除的OrgUserMap对象
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
     * 对OrgUserMap对象执行基础逻辑删除操作，将默认组织设置为非默认。
     *
     * @param model 要执行删除操作的OrgUserMap对象
     */
    public void isDeleteOrgUserMap(OrgUserMap model) {
        model.setDefaultOrg(IsDefaultOrgEnum.NO.getValue());
        super.isDeleteModel(model);
    }
}
