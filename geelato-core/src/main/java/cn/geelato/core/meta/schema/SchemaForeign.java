package cn.geelato.core.meta.schema;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.TableForeign;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @description: SELECT * FROM information_schema.tables
 */
@Getter
@Setter
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
        meta.setEnableStatus(ColumnDefault.ENABLE_STATUS_VALUE);
        meta.setSeqNo(1);

        return meta;
    }
}
