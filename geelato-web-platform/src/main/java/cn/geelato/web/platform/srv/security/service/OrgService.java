package cn.geelato.web.platform.srv.security.service;

import cn.geelato.meta.Org;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.platform.service.BaseSortableService;
import cn.geelato.meta.OrgUserMap;
import cn.geelato.web.platform.srv.security.enums.OrgTypeEnum;
import org.apache.logging.log4j.util.Strings;
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
     * <p>
     * 对指定的模型进行逻辑删除操作，同时清理与之相关的组织用户表和用户表数据。
     *
     * @param model 要进行逻辑删除的模型对象，这里特指Org对象
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
     * <p>
     * 执行全量查询操作，返回满足条件的所有记录。
     *
     * @param params 条件参数，用于指定查询的条件
     * @return 返回包含查询结果的列表，每个元素是一个Map，表示一条记录
     */
    public List<Map<String, Object>> queryTree(Map<String, Object> params) {
        return dao.queryForMapList("query_tree_platform_org", params);
    }

    /**
     * 获取组织所属公司：沿父链取第一个 type=company 的组织，无则回退最顶层祖先。
     *
     * @param id 组织的ID
     * @return 返回组织所属的公司对象（或最顶层祖先），如果未找到则返回null
     */
    public Org getCompany(String id) {
        if (Strings.isBlank(id)) {
            return null;
        }
        Org current = this.getModel(Org.class, id);
        while (current != null) {
            if (OrgTypeEnum.COMPANY.getValue().equals(current.getType())) {
                return current;
            }
            if (Strings.isBlank(current.getPid())) {
                return current;
            }
            Org parent = this.getModel(Org.class, current.getPid());
            if (parent == null) {
                return current;
            }
            current = parent;
        }
        return null;
    }

    public Org getDepartment(String id) {
        if (Strings.isNotBlank(id)) {
            Org model = this.getModel(Org.class, id);
            if (model != null) {
                if (OrgTypeEnum.DEPT.getValue().equals(model.getType())) {
                    return model;
                } else if (Strings.isNotBlank(model.getPid())) {
                    return getDepartment(model.getPid());
                }
            }
        }
        return null;
    }
}
