package cn.geelato.search.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索域：以业务单据为中心的检索建模（如"订单域"、"账单域"）。
 *
 * <p>跨境物流等场景中，一张单据的编号分布在主表多列与子表多行（so号、mbl号、柜号、ams号…），
 * 任意编号命中即应检索到该单据。搜索域将主表列与子表聚合列统一建模为一份文档：
 * 子实体（如柜号表）各行经外键归属主文档，对应字段为多值。
 *
 * <p>字段统一使用实体字段名（fieldName），由接入侧经元数据转换为列名；
 * 主键字段默认 id，与平台 {@code IdEntity} 对齐。
 */
public class SearchDomain {

    /** 域标识（唯一，用作索引目录/索引名）。 */
    private String id;

    /** 主实体名（entityName，非表名）。 */
    private String mainEntity;

    /** 主实体主键字段名。 */
    private String pkField = "id";

    /** 主实体参与检索的编号字段名列表。 */
    private List<String> fields = new ArrayList<>();

    /** 子实体聚合规则列表。 */
    private List<Child> children = new ArrayList<>();

    /**
     * 子实体聚合：子表按外键归属主文档，其编号字段在各行上的取值合并为多值字段。
     */
    public static class Child {

        /** 子实体名。 */
        private String entity;

        /** 子实体上指向主实体主键的外键字段名。 */
        private String fkField;

        /** 子实体参与检索的编号字段名列表。 */
        private List<String> fields = new ArrayList<>();

        public String getEntity() {
            return entity;
        }

        public void setEntity(String entity) {
            this.entity = entity;
        }

        public String getFkField() {
            return fkField;
        }

        public void setFkField(String fkField) {
            this.fkField = fkField;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMainEntity() {
        return mainEntity;
    }

    public void setMainEntity(String mainEntity) {
        this.mainEntity = mainEntity;
    }

    public String getPkField() {
        return pkField;
    }

    public void setPkField(String pkField) {
        this.pkField = pkField;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public List<Child> getChildren() {
        return children;
    }

    public void setChildren(List<Child> children) {
        this.children = children;
    }
}
