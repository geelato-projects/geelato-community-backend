package cn.geelato.mcp.platform.tool.dict;

import cn.geelato.mcp.common.tool.BaseMcpTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据字典查询工具
 * 提供数据字典相关的真实查询功能
 */
@Component
public class DictQueryTool extends BaseMcpTool {

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

    @Tool(description = "获取所有数据字典类型列表")
    public String listAllDictTypes() {
        logToolExecution("listAllDictTypes");
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询字典数据"));
            }
            
            String sql = "SELECT id, dict_code, dict_name, dict_name_en, dict_remark FROM platform_dict WHERE enable_status = 1 AND del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> dicts = jdbcTemplate.queryForList(sql);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> dict : dicts) {
                Map<String, Object> dictInfo = new HashMap<>();
                dictInfo.put("dictId", dict.get("id"));
                dictInfo.put("dictCode", dict.get("dict_code"));
                dictInfo.put("dictName", dict.get("dict_name"));
                dictInfo.put("dictNameEn", dict.get("dict_name_en"));
                dictInfo.put("dictRemark", dict.get("dict_remark"));
                result.add(dictInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("listAllDictTypes", "返回 " + result.size() + " 个字典类型");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllDictTypes", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据字典编码查询字典项列表")
    public String getDictItems(String dictCode) {
        logToolExecution("getDictItems", dictCode);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询字典数据"));
            }
            
            // 先查询字典 ID
            String dictSql = "SELECT id FROM platform_dict WHERE dict_code = ? AND enable_status = 1 AND del_status = 0";
            List<Map<String, Object>> dicts = jdbcTemplate.queryForList(dictSql, dictCode);
            if (dicts.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("字典不存在：" + dictCode));
            }

            String dictId = dicts.get(0).get("id").toString();

            // 查询字典项
            String itemSql = "SELECT id, item_code, item_name, item_name_en, item_color, item_tag, item_remark, seq_no FROM platform_dict_item WHERE dict_id = ? AND enable_status = 1 AND del_status = 0 ORDER BY seq_no";
            List<Map<String, Object>> items = jdbcTemplate.queryForList(itemSql, dictId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> itemInfo = new HashMap<>();
                itemInfo.put("itemId", item.get("id"));
                itemInfo.put("itemCode", item.get("item_code"));
                itemInfo.put("itemName", item.get("item_name"));
                itemInfo.put("itemNameEn", item.get("item_name_en"));
                itemInfo.put("itemColor", item.get("item_color"));
                itemInfo.put("itemTag", item.get("item_tag"));
                itemInfo.put("itemRemark", item.get("item_remark"));
                itemInfo.put("seqNo", item.get("seq_no"));
                result.add(itemInfo);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getDictItems", "返回字典 " + dictCode + " 的 " + result.size() + " 个项");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getDictItems", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据字典编码和字典项编码查询字典项详细信息")
    public String getDictItemDetail(String dictCode, String itemCode) {
        logToolExecution("getDictItemDetail", dictCode, itemCode);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询字典数据"));
            }
            
            // 先查询字典 ID
            String dictSql = "SELECT id FROM platform_dict WHERE dict_code = ? AND enable_status = 1 AND del_status = 0";
            List<Map<String, Object>> dicts = jdbcTemplate.queryForList(dictSql, dictCode);
            if (dicts.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("字典不存在：" + dictCode));
            }

            String dictId = dicts.get(0).get("id").toString();

            // 查询指定字典项
            String itemSql = "SELECT id, item_code, item_name, item_name_en, item_color, item_tag, item_remark, item_extra, seq_no FROM platform_dict_item WHERE dict_id = ? AND item_code = ? AND enable_status = 1 AND del_status = 0";
            List<Map<String, Object>> items = jdbcTemplate.queryForList(itemSql, dictId, itemCode);
            if (items.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("字典项不存在: " + itemCode));
            }

            Map<String, Object> item = items.get(0);
            Map<String, Object> itemInfo = new HashMap<>();
            itemInfo.put("itemId", item.get("id"));
            itemInfo.put("itemCode", item.get("item_code"));
            itemInfo.put("itemName", item.get("item_name"));
            itemInfo.put("itemNameEn", item.get("item_name_en"));
            itemInfo.put("itemColor", item.get("item_color"));
            itemInfo.put("itemTag", item.get("item_tag"));
            itemInfo.put("itemRemark", item.get("item_remark"));
            itemInfo.put("itemExtra", item.get("item_extra"));
            itemInfo.put("seqNo", item.get("seq_no"));

            String jsonResult = JSON.toJSONString(createSuccessResponse(itemInfo));
            logToolResult("getDictItemDetail", "返回字典项详情");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getDictItemDetail", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据字典编码查询字典的完整信息，包括字典类型和所有字典项")
    public String getDictFullInfo(String dictCode) {
        logToolExecution("getDictFullInfo", dictCode);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询字典数据"));
            }
            
            // 查询字典
            String dictSql = "SELECT id, dict_code, dict_name, dict_name_en, dict_remark, dict_color FROM platform_dict WHERE dict_code = ? AND enable_status = 1 AND del_status = 0";
            List<Map<String, Object>> dicts = jdbcTemplate.queryForList(dictSql, dictCode);
            if (dicts.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("字典不存在: " + dictCode));
            }

            Map<String, Object> dict = dicts.get(0);
            Map<String, Object> dictInfo = new HashMap<>();
            dictInfo.put("dictId", dict.get("id"));
            dictInfo.put("dictCode", dict.get("dict_code"));
            dictInfo.put("dictName", dict.get("dict_name"));
            dictInfo.put("dictNameEn", dict.get("dict_name_en"));
            dictInfo.put("dictRemark", dict.get("dict_remark"));
            dictInfo.put("dictColor", dict.get("dict_color"));

            // 查询字典项
            String itemsJson = getDictItems(dictCode);
            if (!itemsJson.startsWith("字典不存在") && !itemsJson.startsWith("查询失败") && !itemsJson.startsWith("数据库连接不可用")) {
                List<Map<String, Object>> items = JSON.parseObject(itemsJson, List.class);
                dictInfo.put("items", items);
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(dictInfo));
            logToolResult("getDictFullInfo", "返回字典 " + dictCode + " 的完整信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getDictFullInfo", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "检查字典是否存在")
    public String checkDictExists(String dictCode) {
        logToolExecution("checkDictExists", dictCode);
        try {
            if (jdbcTemplate == null) {
                return JSON.toJSONString(createErrorResponse("数据库连接不可用，无法查询字典数据"));
            }
            
            String sql = "SELECT COUNT(*) as count FROM platform_dict WHERE dict_code = ? AND enable_status = 1 AND del_status = 0";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, dictCode);
            boolean exists = count != null && count > 0;

            Map<String, Object> result = new HashMap<>();
            result.put("dictCode", dictCode);
            result.put("exists", exists);

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("checkDictExists", "字典 " + dictCode + " " + (exists ? "存在" : "不存在"));
            return jsonResult;
        } catch (Exception e) {
            logToolError("checkDictExists", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }
}
