package cn.geelato.mcp.platform.tool.meta;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.mcp.common.tool.BaseMcpTool;
import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 元数据模型工具
 * 提供实体模型元数据相关的查询功能
 */
@Component
public class MetaModelTool extends BaseMcpTool {

    private final MetaManager metaManager = MetaManager.singleInstance();

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

    @Tool(description = "获取所有实体名称列表")
    public String listAllEntityNames() {
        logToolExecution("listAllEntityNames");
        try {
            Collection<String> entityNames = metaManager.getAllEntityNames();

            if (entityNames == null || entityNames.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("暂无可用的实体元数据"));
            }

            List<String> sortedNames = new ArrayList<>(entityNames);
            Collections.sort(sortedNames);

            String jsonResult = JSON.toJSONString(createSuccessResponse(sortedNames));
            logToolResult("listAllEntityNames", "返回 " + sortedNames.size() + " 个实体名称");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllEntityNames", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "获取所有实体模型的精简信息列表")
    public String listAllEntityLiteMetas() {
        logToolExecution("listAllEntityLiteMetas");
        try {
            Collection<String> entityNames = metaManager.getAllEntityNames();

            if (entityNames == null || entityNames.isEmpty()) {
                return JSON.toJSONString(createErrorResponse("暂无可用的实体元数据"));
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (String entityName : entityNames) {
                EntityMeta entityMeta = metaManager.getByEntityName(entityName);
                if (entityMeta != null) {
                    Map<String, Object> entityInfo = new HashMap<>();
                    entityInfo.put("entityName", entityName);
                    entityInfo.put("entityTitle", entityMeta.getEntityTitle());
                    entityInfo.put("tableName", entityMeta.getTableName());
                    entityInfo.put("description", entityMeta.getEntityTitle());
                    result.add(entityInfo);
                }
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("listAllEntityLiteMetas", "返回 " + result.size() + " 个实体精简信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("listAllEntityLiteMetas", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据实体名称查询实体的详细信息，包括字段列表")
    public String getEntityMeta(String entityName) {
        logToolExecution("getEntityMeta", entityName);
        try {
            EntityMeta entityMeta = metaManager.getByEntityName(entityName);

            if (entityMeta == null) {
                return JSON.toJSONString(createErrorResponse("实体不存在：" + entityName));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("entityName", entityName);
            result.put("entityTitle", entityMeta.getEntityTitle());
            result.put("tableName", entityMeta.getTableName());
            result.put("description", entityMeta.getEntityTitle());
            if (entityMeta.getId() != null) {
                result.put("idField", entityMeta.getId().getFieldName());
            }

            // 添加字段信息
            List<Map<String, Object>> fields = new ArrayList<>();
            if (entityMeta.getFieldMetas() != null) {
                for (FieldMeta fieldMeta : entityMeta.getFieldMetas()) {
                    Map<String, Object> fieldInfo = buildFieldInfo(fieldMeta);
                    fields.add(fieldInfo);
                }
            }
            result.put("fields", fields);

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getEntityMeta", "返回实体 " + entityName + " 的元数据，包含 " + fields.size() + " 个字段");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getEntityMeta", e);
            return JSON.toJSONString(createErrorResponse("查询失败：" + e.getMessage()));
        }
    }

    /**
     * 构建字段信息，包含丰富的元数据
     */
    private Map<String, Object> buildFieldInfo(FieldMeta fieldMeta) {
        Map<String, Object> fieldInfo = new HashMap<>();
        
        // 基本信息
        fieldInfo.put("fieldName", fieldMeta.getFieldName());
        fieldInfo.put("fieldTitle", fieldMeta.getTitle());
        fieldInfo.put("columnName", fieldMeta.getColumnName());
        
        // 数据类型信息
        fieldInfo.put("dataType", fieldMeta.getColumnMeta().getDataType());
        fieldInfo.put("type", fieldMeta.getColumnMeta().getType());
        fieldInfo.put("defaultValue", fieldMeta.getColumnMeta().getDefaultValue());
        
        // 约束信息
        fieldInfo.put("isNullable", fieldMeta.getColumnMeta().isNullable());
        fieldInfo.put("isId", fieldMeta.getColumnMeta().isKey());
        fieldInfo.put("isForeignKey", fieldMeta.getColumnMeta().getIsRefColumn());
        
        return fieldInfo;
    }

    @Tool(description = "根据实体名称查询实体的所有字段信息")
    public String getEntityFields(String entityName) {
        logToolExecution("getEntityFields", entityName);
        try {
            EntityMeta entityMeta = metaManager.getByEntityName(entityName);

            if (entityMeta == null) {
                return JSON.toJSONString(createErrorResponse("实体不存在: " + entityName));
            }

            List<Map<String, Object>> fields = new ArrayList<>();
            if (entityMeta.getFieldMetas() != null) {
                for (FieldMeta fieldMeta : entityMeta.getFieldMetas()) {
                    Map<String, Object> fieldInfo = new HashMap<>();
                    fieldInfo.put("fieldName", fieldMeta.getFieldName());
                    fieldInfo.put("fieldTitle", fieldMeta.getTitle());
                    fieldInfo.put("dataType", fieldMeta.getColumnMeta().getDataType());
                    fieldInfo.put("columnName", fieldMeta.getColumnName());
                    fieldInfo.put("isNullable", fieldMeta.getColumnMeta().isNullable());
                    fieldInfo.put("isId", fieldMeta.getColumnMeta().isKey());
                    fields.add(fieldInfo);
                }
            }

            String jsonResult = JSON.toJSONString(createSuccessResponse(fields));
            logToolResult("getEntityFields", "返回实体 " + entityName + " 的 " + fields.size() + " 个字段");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getEntityFields", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "根据实体名称查询实体的完整元数据信息（已废弃，请使用 getEntityMeta）")
    @Deprecated
    public String getEntityFullMeta(String entityName) {
        return getEntityMeta(entityName);
    }

    @Tool(description = "检查实体是否存在")
    public String checkEntityExists(String entityName) {
        logToolExecution("checkEntityExists", entityName);
        try {
            EntityMeta entityMeta = metaManager.getByEntityName(entityName);
            boolean exists = entityMeta != null;

            Map<String, Object> result = new HashMap<>();
            result.put("entityName", entityName);
            result.put("exists", exists);

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("checkEntityExists", "实体 " + entityName + " " + (exists ? "存在" : "不存在"));
            return jsonResult;
        } catch (Exception e) {
            logToolError("checkEntityExists", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "获取实体的统计信息")
    public String getMetaStatistics() {
        logToolExecution("getMetaStatistics");
        try {
            Collection<String> entityNames = metaManager.getAllEntityNames();

            int totalEntities = entityNames != null ? entityNames.size() : 0;
            int totalFields = 0;

            if (entityNames != null) {
                for (String entityName : entityNames) {
                    EntityMeta entityMeta = metaManager.getByEntityName(entityName);
                    if (entityMeta != null && entityMeta.getFieldMetas() != null) {
                        totalFields += entityMeta.getFieldMetas().size();
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("totalEntities", totalEntities);
            result.put("totalFields", totalFields);

            String jsonResult = JSON.toJSONString(createSuccessResponse(result));
            logToolResult("getMetaStatistics", "返回元数据统计信息");
            return jsonResult;
        } catch (Exception e) {
            logToolError("getMetaStatistics", e);
            return JSON.toJSONString(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }
}
