package cn.geelato.core.meta.schema;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.meta.model.entity.TableForeign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @description: SELECT * FROM information_schema.tables
 * @date 2023/6/16 11:44
 */
public class SchemaForeign implements Serializable {
    private String constraintCatalog;
    private String constraintSchema;
    private String constraintName;
    private String constraintType;
    private String enforced;
    private String tableSchema;
    private String tableName;
    private String columnName;
    private String referencedTableSchema;
    private String referencedTableName;
    private String referencedColumnName;
    private String updateRule;
    private String deleteRule;

    public static List<SchemaForeign> buildTableForeignKeys(List<Map<String, Object>> mapList) {
        List<SchemaForeign> schemaForeigns = new ArrayList<>();
        if (mapList != null && !mapList.isEmpty()) {
            for (Map<String, Object> map : mapList) {
                schemaForeigns.add(SchemaForeign.buildTableForeignKey(map));
            }
        }
        return schemaForeigns;
    }

    public static SchemaForeign buildTableForeignKey(Map<String, Object> map) {
        SchemaForeign key = new SchemaForeign();
        key.setTableName(map.get("TABLE_NAME") == null ? null : map.get("TABLE_NAME").toString());
        key.setConstraintType(map.get("CONSTRAINT_TYPE") == null ? null : map.get("CONSTRAINT_TYPE").toString());
        key.setConstraintName(map.get("CONSTRAINT_NAME") == null ? null : map.get("CONSTRAINT_NAME").toString());
        key.setReferencedTableName(map.get("REFERENCED_TABLE_NAME") == null ? null : map.get("REFERENCED_TABLE_NAME").toString());
        key.setReferencedColumnName(map.get("REFERENCED_COLUMN_NAME") == null ? null : map.get("REFERENCED_COLUMN_NAME").toString());

        return key;
    }

    public String getConstraintCatalog() {
        return constraintCatalog;
    }

    public void setConstraintCatalog(String constraintCatalog) {
        this.constraintCatalog = constraintCatalog;
    }

    public String getConstraintSchema() {
        return constraintSchema;
    }

    public void setConstraintSchema(String constraintSchema) {
        this.constraintSchema = constraintSchema;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public String getConstraintType() {
        return constraintType;
    }

    public void setConstraintType(String constraintType) {
        this.constraintType = constraintType;
    }

    public String getEnforced() {
        return enforced;
    }

    public void setEnforced(String enforced) {
        this.enforced = enforced;
    }

    public String getTableSchema() {
        return tableSchema;
    }

    public void setTableSchema(String tableSchema) {
        this.tableSchema = tableSchema;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getReferencedTableSchema() {
        return referencedTableSchema;
    }

    public void setReferencedTableSchema(String referencedTableSchema) {
        this.referencedTableSchema = referencedTableSchema;
    }

    public String getReferencedTableName() {
        return referencedTableName;
    }

    public void setReferencedTableName(String referencedTableName) {
        this.referencedTableName = referencedTableName;
    }

    public String getReferencedColumnName() {
        return referencedColumnName;
    }

    public void setReferencedColumnName(String referencedColumnName) {
        this.referencedColumnName = referencedColumnName;
    }

    public String getUpdateRule() {
        return updateRule;
    }

    public void setUpdateRule(String updateRule) {
        this.updateRule = updateRule;
    }

    public String getDeleteRule() {
        return deleteRule;
    }

    public void setDeleteRule(String deleteRule) {
        this.deleteRule = deleteRule;
    }

    public TableForeign convertTableForeign(TableForeign meta) {
        meta = meta == null ? new TableForeign() : meta;
        meta.setMainTableSchema(this.tableSchema);
        meta.setMainTableId(null);
        meta.setMainTable(this.tableName);
        meta.setMainTableCol(this.columnName);
        meta.setForeignTableSchema(this.referencedTableSchema);
        meta.setForeignTableId(null);
        meta.setForeignTable(this.referencedTableName);
        meta.setForeignTableCol(this.referencedColumnName);
        meta.setDeleteAction(this.deleteRule);
        meta.setUpdateAction(this.updateRule);
        meta.setEnableStatus(EnableStatusEnum.ENABLED.getCode());
        meta.setSeqNo(1);

        return meta;
    }
}
