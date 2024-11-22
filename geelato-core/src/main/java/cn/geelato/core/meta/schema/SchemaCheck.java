package cn.geelato.core.meta.schema;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SchemaCheck implements Serializable {
    private String constraintCatalog;
    private String constraintSchema;
    private String constraintName;
    private String checkClause;
    private String tableSchema;
    private String tableName;
    private String constraintType;
    private String enforced;

    public static List<SchemaCheck> buildData(List<Map<String, Object>> list) {
        List<SchemaCheck> schemas = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (Map<String, Object> map : list) {
                schemas.add(buildData(map));
            }
        }
        return schemas;
    }

    public static SchemaCheck buildData(Map<String, Object> map) {
        SchemaCheck schema = new SchemaCheck();
        schema.setConstraintCatalog(map.get("CONSTRAINT_CATALOG") == null ? null : map.get("CONSTRAINT_CATALOG").toString());
        schema.setConstraintSchema(map.get("CONSTRAINT_SCHEMA") == null ? null : map.get("CONSTRAINT_SCHEMA").toString());
        schema.setConstraintName(map.get("CONSTRAINT_NAME") == null ? null : map.get("CONSTRAINT_NAME").toString());
        schema.setCheckClause(map.get("CHECK_CLAUSE") == null ? null : map.get("CHECK_CLAUSE").toString());
        schema.setTableSchema(map.get("TABLE_SCHEMA") == null ? null : map.get("TABLE_SCHEMA").toString());
        schema.setTableName(map.get("TABLE_NAME") == null ? null : map.get("TABLE_NAME").toString());
        schema.setConstraintType(map.get("CONSTRAINT_TYPE") == null ? null : map.get("CONSTRAINT_TYPE").toString());
        schema.setEnforced(map.get("ENFORCED") == null ? null : map.get("ENFORCED").toString());

        return schema;
    }
}
