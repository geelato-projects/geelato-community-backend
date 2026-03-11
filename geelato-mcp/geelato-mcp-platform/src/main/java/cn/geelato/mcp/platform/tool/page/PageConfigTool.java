package cn.geelato.mcp.platform.tool.page;

import cn.geelato.mcp.common.tool.BaseMcpTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 页面配置查询工具
 * 提供页面配置相关的真实查询功能
 */
@Component
public class PageConfigTool extends BaseMcpTool {

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

    @Tool(description = "获取所有页面配置列表")
    public String listAllPages() {
        logToolExecution("listAllPages");
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询页面数据"));
            }

            String sql = "SELECT id, code, title, type, description, version FROM platform_app_page WHERE del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> pages = jdbcTemplate.queryForList(sql);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> page : pages) {
                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageId", page.get("id"));
                pageInfo.put("code", page.get("code"));
                pageInfo.put("title", page.get("title"));
                pageInfo.put("type", page.get("type"));
                pageInfo.put("description", page.get("description"));
                pageInfo.put("version", page.get("version"));
                result.add(pageInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("listAllPages", "返回 " + result.size() + " 个页面配置");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllPages", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据页面ID查询页面配置信息")
    public String getPageConfig(String pageId) {
        logToolExecution("getPageConfig", pageId);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询页面数据"));
            }

            String sql = "SELECT id, code, title, type, description, source_content, preview_content, release_content, version FROM platform_app_page WHERE id = ? AND del_status = 0";
            List<Map<String, Object>> pages = jdbcTemplate.queryForList(sql, pageId);

            if (pages.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("页面不存在: " + pageId));
            }

            Map<String, Object> page = pages.get(0);
            Map<String, Object> pageConfig = new HashMap<>();
            pageConfig.put("pageId", page.get("id"));
            pageConfig.put("code", page.get("code"));
            pageConfig.put("title", page.get("title"));
            pageConfig.put("type", page.get("type"));
            pageConfig.put("description", page.get("description"));
            pageConfig.put("sourceContent", page.get("source_content"));
            pageConfig.put("previewContent", page.get("preview_content"));
            pageConfig.put("releaseContent", page.get("release_content"));
            pageConfig.put("version", page.get("version"));

            String jsonResult = JSON.toJSONString(createSuccessResponse(pageConfig));
            logToolResult("getPageConfig", "返回页面 " + pageId + " 的配置");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getPageConfig", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据页面编码查询页面配置信息")
    public String getPageByCode(String code) {
        logToolExecution("getPageByCode", code);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询页面数据"));
            }

            String sql = "SELECT id, code, title, type, description, source_content, preview_content, release_content, version FROM platform_app_page WHERE code = ? AND del_status = 0";
            List<Map<String, Object>> pages = jdbcTemplate.queryForList(sql, code);

            if (pages.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("页面不存在: " + code));
            }

            Map<String, Object> page = pages.get(0);
            Map<String, Object> pageConfig = new HashMap<>();
            pageConfig.put("pageId", page.get("id"));
            pageConfig.put("code", page.get("code"));
            pageConfig.put("title", page.get("title"));
            pageConfig.put("type", page.get("type"));
            pageConfig.put("description", page.get("description"));
            pageConfig.put("sourceContent", page.get("source_content"));
            pageConfig.put("previewContent", page.get("preview_content"));
            pageConfig.put("releaseContent", page.get("release_content"));
            pageConfig.put("version", page.get("version"));

            String jsonResult = JSON.toJSONString(createSuccessResponse(pageConfig));
            logToolResult("getPageByCode", "返回页面 " + code + " 的配置");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getPageByCode", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据应用ID查询该应用下的所有页面")
    public String getPagesByApp(String appId) {
        logToolExecution("getPagesByApp", appId);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询页面数据"));
            }

            String sql = "SELECT id, code, title, type, description, version FROM platform_app_page WHERE app_id = ? AND del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> pages = jdbcTemplate.queryForList(sql, appId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> page : pages) {
                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageId", page.get("id"));
                pageInfo.put("code", page.get("code"));
                pageInfo.put("title", page.get("title"));
                pageInfo.put("type", page.get("type"));
                pageInfo.put("description", page.get("description"));
                pageInfo.put("version", page.get("version"));
                result.add(pageInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getPagesByApp", "返回应用 " + appId + " 的 " + result.size() + " 个页面");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getPagesByApp", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据实体名称查询该实体关联的页面列表")
    public String getPagesByEntity(String entityName) {
        logToolExecution("getPagesByEntity", entityName);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询页面数据"));
            }

            // 查询 source_content 中包含该实体的页面
            String sql = "SELECT id, code, title, type, description, version FROM platform_app_page WHERE source_content LIKE ? AND del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> pages = jdbcTemplate.queryForList(sql, "%" + entityName + "%");

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> page : pages) {
                Map<String, Object> pageInfo = new HashMap<>();
                pageInfo.put("pageId", page.get("id"));
                pageInfo.put("code", page.get("code"));
                pageInfo.put("title", page.get("title"));
                pageInfo.put("type", page.get("type"));
                pageInfo.put("description", page.get("description"));
                pageInfo.put("version", page.get("version"));
                result.add(pageInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getPagesByEntity", "返回实体 " + entityName + " 的 " + result.size() + " 个页面");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getPagesByEntity", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }
}
