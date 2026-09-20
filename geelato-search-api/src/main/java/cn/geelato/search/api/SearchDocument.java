package cn.geelato.search.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台中立的索引文档。以主实体主键为文档 id，主表字段与子表聚合字段统一为多值模型
 * （主表字段单值，子表字段按行多值）。
 *
 * <p>tenantCode/delStatus/appId 为过滤字段：检索时与查询条件匹配，保证 id 集合
 * 不跨租户、不包含软删数据（与 SQL 侧过滤语义一致）。
 */
public class SearchDocument {

    private String id;
    private String tenantCode;
    private String delStatus;
    private String appId;

    /** 字段名 → 值列表（主表字段单值列表，子表聚合字段多值）。 */
    private Map<String, List<String>> fieldValues = new HashMap<>();

    public SearchDocument() {
    }

    public SearchDocument(String id) {
        this.id = id;
    }

    public void addFieldValue(String field, String value) {
        if (value == null) {
            return;
        }
        fieldValues.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
    }

    public List<String> getFieldValues(String field) {
        List<String> values = fieldValues.get(field);
        return values == null ? List.of() : values;
    }

    public String getFirstValue(String field) {
        List<String> values = fieldValues.get(field);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getDelStatus() {
        return delStatus;
    }

    public void setDelStatus(String delStatus) {
        this.delStatus = delStatus;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Map<String, List<String>> getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(Map<String, List<String>> fieldValues) {
        this.fieldValues = fieldValues;
    }
}
