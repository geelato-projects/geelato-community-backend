package cn.geelato.mcp.platform.controller;

import cn.geelato.mcp.platform.tool.dict.DictQueryTool;
import cn.geelato.mcp.platform.tool.meta.MetaModelTool;
import cn.geelato.mcp.platform.tool.page.PageConfigTool;
import cn.geelato.mcp.platform.tool.system.SystemInfoTool;
import cn.geelato.mcp.platform.tool.user.UserQueryTool;
import cn.geelato.mcp.platform.tool.view.ViewQueryTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tool HTTP API Controller
 * 提供HTTP接口直接调用MCP工具
 */
@RestController
@RequestMapping("/api/mcp")
public class McpToolController {

    @Autowired
    private SystemInfoTool systemInfoTool;

    @Autowired
    private UserQueryTool userQueryTool;

    @Autowired
    private DictQueryTool dictQueryTool;

    @Autowired
    private PageConfigTool pageConfigTool;

    @Autowired
    private ViewQueryTool viewQueryTool;

    @Autowired
    private MetaModelTool metaModelTool;

    /**
     * 统一工具调用接口
     */
    @PostMapping("/tool/call")
    public ResponseEntity<Map<String, Object>> callTool(@RequestBody ToolCallRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Object data = executeToolMethod(request.getTool(), request.getMethod(), request.getParams());
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取所有可用工具列表
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> tools = new HashMap<>();

        // SystemInfoTool
        Map<String, Object> systemTools = new HashMap<>();
        systemTools.put("system_get_info", "系统：获取系统基本信息");
        systemTools.put("system_get_memory_info", "系统：获取JVM内存使用情况");
        systemTools.put("system_get_cpu_info", "系统：获取CPU信息");
        systemTools.put("system_get_env", "系统：获取环境变量列表");
        systemTools.put("system_get_props", "系统：获取系统属性列表");
        tools.put("SystemInfoTool", systemTools);

        // UserQueryTool
        Map<String, Object> userTools = new HashMap<>();
        userTools.put("user_list_all", "用户：获取所有用户列表");
        userTools.put("user_get_by_id", "用户：根据ID查询用户信息");
        userTools.put("user_get_by_username", "用户：根据用户名查询用户信息");
        userTools.put("user_list_roles", "用户：获取所有角色列表");
        userTools.put("user_get_roles", "用户：获取用户角色");
        userTools.put("user_get_role_perms", "用户：根据角色编码查询权限");
        userTools.put("user_check_permission", "用户：检查用户权限");
        tools.put("UserQueryTool", userTools);

        // DictQueryTool
        Map<String, Object> dictTools = new HashMap<>();
        dictTools.put("dict_list_types", "字典：获取所有字典类型");
        dictTools.put("dict_list_items", "字典：获取字典项列表");
        dictTools.put("dict_list_all", "字典：获取字典完整信息");
        dictTools.put("dict_get_detail", "字典：获取字典项详情");
        dictTools.put("dict_exists", "字典：检查字典是否存在");
        tools.put("DictQueryTool", dictTools);

        // PageConfigTool
        Map<String, Object> pageTools = new HashMap<>();
        pageTools.put("page_list_all", "页面：获取所有页面配置");
        pageTools.put("page_get_detail_by_id", "页面：获取页面配置详情");
        pageTools.put("page_get_detail_by_code", "页面：根据编码获取页面配置详情");
        pageTools.put("page_list_by_app", "页面：根据应用获取页面列表");
        pageTools.put("page_list_by_entity", "页面：获取实体关联页面");
        tools.put("PageConfigTool", pageTools);

        // ViewQueryTool
        Map<String, Object> viewTools = new HashMap<>();
        viewTools.put("view_list_names_all", "视图：获取所有视图名称");
        viewTools.put("view_list_lite_all", "视图：获取所有视图精简信息");
        viewTools.put("view_get_detail", "视图：获取视图元数据");
        viewTools.put("view_list_by_entity", "视图：获取实体关联视图");
        tools.put("ViewQueryTool", viewTools);

        // MetaModelTool
        Map<String, Object> metaTools = new HashMap<>();
        metaTools.put("meta_list_names_all", "元数据：获取所有实体名称");
        metaTools.put("meta_list_lite_all", "元数据：获取所有实体精简信息");
        metaTools.put("meta_get_detail", "元数据：获取实体详情");
        metaTools.put("meta_check_exists", "元数据：检查实体是否存在");
        metaTools.put("meta_get_statistics", "元数据：获取统计信息");
        tools.put("MetaModelTool", metaTools);

        result.put("code", 200);
        result.put("message", "success");
        result.put("data", tools);
        return ResponseEntity.ok(result);
    }

    private Object executeToolMethod(String toolName, String methodName, Map<String, Object> params) throws Exception {
        switch (toolName) {
            case "SystemInfoTool":
                return executeSystemInfoTool(methodName);
            case "UserQueryTool":
                return executeUserQueryTool(methodName, params);
            case "DictQueryTool":
                return executeDictQueryTool(methodName, params);
            case "PageConfigTool":
                return executePageConfigTool(methodName, params);
            case "ViewQueryTool":
                return executeViewQueryTool(methodName, params);
            case "MetaModelTool":
                return executeMetaModelTool(methodName, params);
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }

    private Object executeSystemInfoTool(String methodName) throws Exception {
        switch (methodName) {
            case "system_get_info":
                return JSON.parse(systemInfoTool.getSystemInfo());
            case "system_get_memory_info":
                return JSON.parse(systemInfoTool.getMemoryInfo());
            case "system_get_cpu_info":
                return JSON.parse(systemInfoTool.getCpuInfo());
            case "system_get_env":
                return JSON.parse(systemInfoTool.getEnvironmentVariables());
            case "system_get_props":
                return JSON.parse(systemInfoTool.getSystemProperties());
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeUserQueryTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "user_list_all":
                return JSON.parse(userQueryTool.listAllUsers());
            case "user_get_by_id":
                String userId = params != null ? (String) params.get("userId") : null;
                return JSON.parse(userQueryTool.getUserById(userId));
            case "user_get_by_username":
                String username = params != null ? (String) params.get("username") : null;
                return JSON.parse(userQueryTool.getUserByUsername(username));
            case "user_list_roles":
                return JSON.parse(userQueryTool.listAllRoles());
            case "user_get_roles":
                String uid = params != null ? (String) params.get("userId") : null;
                return JSON.parse(userQueryTool.getUserRoles(uid));
            case "user_get_role_perms":
                String roleCode = params != null ? (String) params.get("roleCode") : null;
                return JSON.parse(userQueryTool.getRolePermissions(roleCode));
            case "user_check_permission":
                String checkUid = params != null ? (String) params.get("userId") : null;
                String permission = params != null ? (String) params.get("permission") : null;
                return JSON.parse(userQueryTool.checkUserPermission(checkUid, permission));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeDictQueryTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "dict_list_types":
                return JSON.parse(dictQueryTool.listAllDictTypes());
            case "dict_list_items":
                String dictCode = params != null ? (String) params.get("dictCode") : null;
                return JSON.parse(dictQueryTool.getDictItems(dictCode));
            case "dict_list_all":
                String fullDictCode = params != null ? (String) params.get("dictCode") : null;
                return JSON.parse(dictQueryTool.getDictFullInfo(fullDictCode));
            case "dict_get_detail":
                String itemDictCode = params != null ? (String) params.get("dictCode") : null;
                String itemCode = params != null ? (String) params.get("itemCode") : null;
                return JSON.parse(dictQueryTool.getDictItemDetail(itemDictCode, itemCode));
            case "dict_exists":
                String checkDictCode = params != null ? (String) params.get("dictCode") : null;
                return JSON.parse(dictQueryTool.checkDictExists(checkDictCode));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executePageConfigTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "page_list_all":
                return JSON.parse(pageConfigTool.listAllPages());
            case "page_get_detail_by_id":
                String pageId = params != null ? (String) params.get("pageId") : null;
                return JSON.parse(pageConfigTool.getPageConfig(pageId));
            case "page_get_detail_by_code":
                String pageCode = params != null ? (String) params.get("code") : null;
                return JSON.parse(pageConfigTool.getPageByCode(pageCode));
            case "page_list_by_app":
                String appId = params != null ? (String) params.get("appId") : null;
                return JSON.parse(pageConfigTool.getPagesByApp(appId));
            case "page_list_by_entity":
                String entityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(pageConfigTool.getPagesByEntity(entityName));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeViewQueryTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "view_list_names_all":
                return JSON.parse(viewQueryTool.listAllViewNames());
            case "view_list_lite_all":
                return JSON.parse(viewQueryTool.listAllViewLiteMetas());
            case "view_get_detail":
                String viewName = params != null ? (String) params.get("viewName") : null;
                return JSON.parse(viewQueryTool.getViewMeta(viewName));
            case "view_list_by_entity":
                String entityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(viewQueryTool.getViewsByEntity(entityName));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeMetaModelTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "meta_list_names_all":
                return JSON.parse(metaModelTool.listAllEntityNames());
            case "meta_list_lite_all":
                return JSON.parse(metaModelTool.listAllEntityLiteMetas());
            case "meta_get_detail":
                String entityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(metaModelTool.getEntityMeta(entityName));
            case "meta_check_exists":
                String checkEntityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(metaModelTool.checkEntityExists(checkEntityName));
            case "meta_get_statistics":
                return JSON.parse(metaModelTool.getMetaStatistics());
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    /**
     * 工具调用请求
     */
    public static class ToolCallRequest {
        private String tool;
        private String method;
        private Map<String, Object> params;

        public String getTool() {
            return tool;
        }

        public void setTool(String tool) {
            this.tool = tool;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params;
        }
    }
}
