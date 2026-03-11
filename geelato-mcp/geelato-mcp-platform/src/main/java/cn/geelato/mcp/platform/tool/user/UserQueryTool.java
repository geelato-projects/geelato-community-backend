package cn.geelato.mcp.platform.tool.user;

import cn.geelato.mcp.common.tool.BaseMcpTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 用户查询工具
 * 提供用户和权限相关的真实查询功能
 */
@Component
public class UserQueryTool extends BaseMcpTool {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }

    private Map<String, Object> createSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "查询成功");
        response.put("data", data);
        return response;
    }

    @Tool(description = "获取所有用户列表")
    public String listAllUsers() {
        logToolExecution("listAllUsers");
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询用户数据"));
            }
            
            String sql = "SELECT id, login_name, name, en_name, email, mobile_phone, orgName, enable_status FROM platform_user WHERE enable_status = 1 AND del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> user : users) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userId", user.get("id"));
                userInfo.put("loginName", user.get("login_name"));
                userInfo.put("name", user.get("name"));
                userInfo.put("enName", user.get("en_name"));
                userInfo.put("email", user.get("email"));
                userInfo.put("mobilePhone", user.get("mobile_phone"));
                userInfo.put("orgName", user.get("org_name"));
                Object enableStatus = user.get("enable_status");
                boolean isEnabled = false;
                if (enableStatus != null) {
                    if (enableStatus instanceof Boolean) {
                        isEnabled = (Boolean) enableStatus;
                    } else if (enableStatus instanceof Number) {
                        isEnabled = ((Number) enableStatus).intValue() == 1;
                    } else {
                        isEnabled = "1".equals(enableStatus.toString()) || "true".equalsIgnoreCase(enableStatus.toString());
                    }
                }
                userInfo.put("status", isEnabled ? "正常" : "禁用");
                result.add(userInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("listAllUsers", "返回 " + result.size() + " 个用户");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllUsers", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据用户ID查询用户信息")
    public String getUserById(String userId) {
        logToolExecution("getUserById", userId);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询用户数据"));
            }
            
            String sql = "SELECT id, login_name, name, en_name, email, mobile_phone, telephone, orgName, post, job_number, address, enable_status FROM platform_user WHERE id = ? AND del_status = 0";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, userId);

            if (users.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("用户不存在: " + userId));
            }

            Map<String, Object> user = users.get(0);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", user.get("id"));
            userInfo.put("loginName", user.get("login_name"));
            userInfo.put("name", user.get("name"));
            userInfo.put("enName", user.get("en_name"));
            userInfo.put("email", user.get("email"));
            userInfo.put("mobilePhone", user.get("mobile_phone"));
            userInfo.put("telephone", user.get("telephone"));
            userInfo.put("orgName", user.get("org_name"));
            userInfo.put("post", user.get("post"));
            userInfo.put("jobNumber", user.get("job_number"));
            userInfo.put("address", user.get("address"));
            Object enableStatus = user.get("enable_status");
            boolean isEnabled = false;
            if (enableStatus != null) {
                if (enableStatus instanceof Boolean) {
                    isEnabled = (Boolean) enableStatus;
                } else if (enableStatus instanceof Number) {
                    isEnabled = ((Number) enableStatus).intValue() == 1;
                } else {
                    isEnabled = "1".equals(enableStatus.toString()) || "true".equalsIgnoreCase(enableStatus.toString());
                }
            }
            userInfo.put("status", isEnabled ? "正常" : "禁用");

            String jsonResult = JSON.toJSONString(createSuccessResponse(userInfo));
            logToolResult("getUserById", "返回用户 " + userId + " 的信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getUserById", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据用户名查询用户信息")
    public String getUserByUsername(String username) {
        logToolExecution("getUserByUsername", username);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询用户数据"));
            }
            
            String sql = "SELECT id, login_name, name, en_name, email, mobile_phone, orgName, enable_status FROM platform_user WHERE login_name = ? AND enable_status = 1 AND del_status = 0";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, username);

            if (users.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("用户不存在: " + username));
            }

            Map<String, Object> user = users.get(0);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", user.get("id"));
            userInfo.put("loginName", user.get("login_name"));
            userInfo.put("name", user.get("name"));
            userInfo.put("enName", user.get("en_name"));
            userInfo.put("email", user.get("email"));
            userInfo.put("mobilePhone", user.get("mobile_phone"));
            userInfo.put("orgName", user.get("org_name"));
            Object enableStatus = user.get("enable_status");
            boolean isEnabled = false;
            if (enableStatus != null) {
                if (enableStatus instanceof Boolean) {
                    isEnabled = (Boolean) enableStatus;
                } else if (enableStatus instanceof Number) {
                    isEnabled = ((Number) enableStatus).intValue() == 1;
                } else {
                    isEnabled = "1".equals(enableStatus.toString()) || "true".equalsIgnoreCase(enableStatus.toString());
                }
            }
            userInfo.put("status", isEnabled ? "正常" : "禁用");

            String jsonResult = JSON.toJSONString(createSuccessResponse(userInfo));
            logToolResult("getUserByUsername", "返回用户 " + username + " 的信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getUserByUsername", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "获取所有角色列表")
    public String listAllRoles() {
        logToolExecution("listAllRoles");
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询角色数据"));
            }
            
            String sql = "SELECT id, code, name, description FROM platform_role WHERE enable_status = 1 AND del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> roles = jdbcTemplate.queryForList(sql);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> role : roles) {
                Map<String, Object> roleInfo = new HashMap<>();
                roleInfo.put("roleId", role.get("id"));
                roleInfo.put("roleCode", role.get("code"));
                roleInfo.put("roleName", role.get("name"));
                roleInfo.put("description", role.get("description"));
                result.add(roleInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("listAllRoles", "返回 " + result.size() + " 个角色");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllRoles", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据用户ID查询用户的角色列表")
    public String getUserRoles(String userId) {
        logToolExecution("getUserRoles", userId);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询角色数据"));
            }
            
            String sql = "SELECT r.id, r.code, r.name FROM platform_role r " +
                        "INNER JOIN platform_role_r_user rru ON r.id = rru.role_id " +
                        "WHERE rru.user_id = ? AND r.enable_status = 1 AND r.del_status = 0";

            List<Map<String, Object>> roles = jdbcTemplate.queryForList(sql, userId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> role : roles) {
                Map<String, Object> roleInfo = new HashMap<>();
                roleInfo.put("roleId", role.get("id"));
                roleInfo.put("roleCode", role.get("code"));
                roleInfo.put("roleName", role.get("name"));
                result.add(roleInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getUserRoles", "返回用户 " + userId + " 的 " + result.size() + " 个角色");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getUserRoles", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据角色编码查询角色的权限列表")
    public String getRolePermissions(String roleCode) {
        logToolExecution("getRolePermissions", roleCode);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询权限数据"));
            }
            
            // 先查询角色 ID
            String roleSql = "SELECT id FROM platform_role WHERE code = ? AND enable_status = 1 AND del_status = 0";
            List<Map<String, Object>> roles = jdbcTemplate.queryForList(roleSql, roleCode);
            if (roles.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("角色不存在：" + roleCode));
            }

            String roleId = roles.get(0).get("id").toString();

            // 查询角色权限
            String permSql = "SELECT permission_id, permission_name FROM platform_role_r_permission WHERE role_id = ? AND del_status = 0";
            List<Map<String, Object>> perms = jdbcTemplate.queryForList(permSql, roleId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> perm : perms) {
                Map<String, Object> permInfo = new HashMap<>();
                permInfo.put("permissionId", perm.get("permission_id"));
                permInfo.put("permissionName", perm.get("permission_name"));
                result.add(permInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getRolePermissions", "返回角色 " + roleCode + " 的 " + result.size() + " 个权限");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getRolePermissions", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "检查用户是否有指定权限")
    public String checkUserPermission(String userId, String permission) {
        logToolExecution("checkUserPermission", userId, permission);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询权限数据"));
            }
            
            // 查询用户的所有权限
            String sql = "SELECT COUNT(*) as count FROM platform_role_r_permission rrp " +
                        "WHERE rrp.role_id IN (" +
                        "  SELECT role_id FROM platform_role_r_user WHERE user_id = ?" +
                        ") AND (rrp.permission_id = ?) " +
                        "AND rrp.del_status = 0";

            Long count = jdbcTemplate.queryForObject(sql, Long.class, userId, permission);
            boolean hasPermission = count != null && count > 0;

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("permission", permission);
            result.put("hasPermission", hasPermission);

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("checkUserPermission", "用户 " + userId + " " + (hasPermission ? "有" : "没有") + " 权限 " + permission);
            return jsonResult;
        } catch (Exception e) {
            logToolError("checkUserPermission", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }
}
