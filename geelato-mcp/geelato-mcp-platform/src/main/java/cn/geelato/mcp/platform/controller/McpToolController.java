package cn.geelato.mcp.platform.controller;

import cn.geelato.mcp.platform.tool.dict.DictQueryTool;
import cn.geelato.mcp.platform.tool.meta.MetaModelTool;
import cn.geelato.mcp.platform.tool.page.PageConfigTool;
import cn.geelato.mcp.platform.tool.system.SystemInfoTool;
import cn.geelato.mcp.platform.tool.user.UserQueryTool;
import cn.geelato.mcp.platform.tool.view.ViewQueryTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
        systemTools.put("getSystemInfo", "获取系统基本信息");
        systemTools.put("getMemoryInfo", "获取JVM内存使用情况");
        systemTools.put("getCpuInfo", "获取CPU信息");
        systemTools.put("getEnvironmentVariables", "获取环境变量列表");
        systemTools.put("getSystemProperties", "获取系统属性列表");
        tools.put("SystemInfoTool", systemTools);

        // UserQueryTool
        Map<String, Object> userTools = new HashMap<>();
        userTools.put("listAllUsers", "获取所有用户列表");
        userTools.put("getUserById", "根据用户ID查询用户信息");
        userTools.put("getUserByUsername", "根据用户名查询用户信息");
        userTools.put("listAllRoles", "获取所有角色列表");
        userTools.put("getUserRoles", "获取用户角色");
        userTools.put("checkUserPermission", "检查用户权限");
        tools.put("UserQueryTool", userTools);

        // DictQueryTool
        Map<String, Object> dictTools = new HashMap<>();
        dictTools.put("listAllDictTypes", "获取所有字典类型");
        dictTools.put("getDictItems", "获取字典项列表");
        dictTools.put("getDictFullInfo", "获取字典完整信息");
        dictTools.put("getDictItemDetail", "获取字典项详情");
        dictTools.put("checkDictExists", "检查字典是否存在");
        tools.put("DictQueryTool", dictTools);

        // PageConfigTool
        Map<String, Object> pageTools = new HashMap<>();
        pageTools.put("listAllPages", "获取所有页面配置");
        pageTools.put("getPageConfig", "获取页面配置详情");
        pageTools.put("getPagesByEntity", "获取实体关联页面");
        tools.put("PageConfigTool", pageTools);

        // ViewQueryTool
        Map<String, Object> viewTools = new HashMap<>();
        viewTools.put("listAllViewNames", "获取所有视图名称");
        viewTools.put("getViewMeta", "获取视图元数据");
        viewTools.put("getViewColumns", "获取视图列配置");
        viewTools.put("getViewsByEntity", "获取实体关联视图");
        tools.put("ViewQueryTool", viewTools);

        // MetaModelTool
        Map<String, Object> metaTools = new HashMap<>();
        metaTools.put("listAllEntityNames", "获取所有实体名称");
        metaTools.put("listAllEntityLiteMetas", "获取所有实体精简信息");
        metaTools.put("getEntityMeta", "获取实体元数据");
        metaTools.put("getEntityFields", "获取实体字段信息");
        metaTools.put("getEntityFullMeta", "获取实体完整元数据");
        metaTools.put("checkEntityExists", "检查实体是否存在");
        metaTools.put("getMetaStatistics", "获取元数据统计");
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
            case "getSystemInfo":
                return JSON.parse(systemInfoTool.getSystemInfo());
            case "getMemoryInfo":
                return JSON.parse(systemInfoTool.getMemoryInfo());
            case "getCpuInfo":
                return JSON.parse(systemInfoTool.getCpuInfo());
            case "getEnvironmentVariables":
                return JSON.parse(systemInfoTool.getEnvironmentVariables());
            case "getSystemProperties":
                return JSON.parse(systemInfoTool.getSystemProperties());
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeUserQueryTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "listAllUsers":
                return JSON.parse(userQueryTool.listAllUsers());
            case "getUserById":
                String userId = params != null ? (String) params.get("userId") : null;
                return JSON.parse(userQueryTool.getUserById(userId));
            case "getUserByUsername":
                String username = params != null ? (String) params.get("username") : null;
                return JSON.parse(userQueryTool.getUserByUsername(username));
            case "listAllRoles":
                return JSON.parse(userQueryTool.listAllRoles());
            case "getUserRoles":
                String uid = params != null ? (String) params.get("userId") : null;
                return JSON.parse(userQueryTool.getUserRoles(uid));
            case "checkUserPermission":
                String checkUid = params != null ? (String) params.get("userId") : null;
                String permission = params != null ? (String) params.get("permission") : null;
                return JSON.parse(userQueryTool.checkUserPermission(checkUid, permission));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeDictQueryTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "listAllDictTypes":
                return JSON.parse(dictQueryTool.listAllDictTypes());
            case "getDictItems":
                String dictCode = params != null ? (String) params.get("dictCode") : null;
                return JSON.parse(dictQueryTool.getDictItems(dictCode));
            case "getDictFullInfo":
                String fullDictCode = params != null ? (String) params.get("dictCode") : null;
                return JSON.parse(dictQueryTool.getDictFullInfo(fullDictCode));
            case "getDictItemDetail":
                String itemDictCode = params != null ? (String) params.get("dictCode") : null;
                String itemCode = params != null ? (String) params.get("itemCode") : null;
                return JSON.parse(dictQueryTool.getDictItemDetail(itemDictCode, itemCode));
            case "checkDictExists":
                String checkDictCode = params != null ? (String) params.get("dictCode") : null;
                return JSON.parse(dictQueryTool.checkDictExists(checkDictCode));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executePageConfigTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "listAllPages":
                return JSON.parse(pageConfigTool.listAllPages());
            case "getPageConfig":
                String pageId = params != null ? (String) params.get("pageId") : null;
                return JSON.parse(pageConfigTool.getPageConfig(pageId));
            case "getPagesByEntity":
                String entityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(pageConfigTool.getPagesByEntity(entityName));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeViewQueryTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "listAllViewNames":
                return JSON.parse(viewQueryTool.listAllViewNames());
            case "getViewMeta":
                String viewName = params != null ? (String) params.get("viewName") : null;
                return JSON.parse(viewQueryTool.getViewMeta(viewName));
            case "getViewColumns":
                String colViewName = params != null ? (String) params.get("viewName") : null;
                return JSON.parse(viewQueryTool.getViewColumns(colViewName));
            case "getViewsByEntity":
                String entityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(viewQueryTool.getViewsByEntity(entityName));
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private Object executeMetaModelTool(String methodName, Map<String, Object> params) throws Exception {
        switch (methodName) {
            case "listAllEntityNames":
                return JSON.parse(metaModelTool.listAllEntityNames());
            case "listAllEntityLiteMetas":
                return JSON.parse(metaModelTool.listAllEntityLiteMetas());
            case "getEntityMeta":
                String entityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(metaModelTool.getEntityMeta(entityName));
            case "getEntityFields":
                String fieldEntityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(metaModelTool.getEntityFields(fieldEntityName));
            case "getEntityFullMeta":
                String fullEntityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(metaModelTool.getEntityFullMeta(fullEntityName));
            case "checkEntityExists":
                String checkEntityName = params != null ? (String) params.get("entityName") : null;
                return JSON.parse(metaModelTool.checkEntityExists(checkEntityName));
            case "getMetaStatistics":
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
