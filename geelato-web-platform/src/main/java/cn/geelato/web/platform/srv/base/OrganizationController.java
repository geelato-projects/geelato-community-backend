package cn.geelato.web.platform.srv.base;

import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织机构控制器
 */

@ApiRestController("/org")
public class OrganizationController extends BaseController {
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
    
    /**
     * 根据组织ID获取组织数据
     * 
     * @param orgId 组织ID
     * @return 组织数据
     */
    @GetMapping("/{orgId}")
    public Map<String, Object> getOrgById(@PathVariable("orgId") String orgId) {
        Map<String, Object> result = new HashMap<>();
        
        if (orgId == null || orgId.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "组织ID不能为空");
            return result;
        }
        
        try {
            String sql = "SELECT * FROM platform_org WHERE id = ?";
            List<Map<String, Object>> orgList = jdbcTemplate.queryForList(sql, orgId);
            
            if (!orgList.isEmpty()) {
                result.put("success", true);
                result.put("data", orgList.get(0));
            } else {
                result.put("success", false);
                result.put("message", "未找到对应的组织数据");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取组织数据失败: " + e.getMessage());
        }
        
        return result;
    }
}
