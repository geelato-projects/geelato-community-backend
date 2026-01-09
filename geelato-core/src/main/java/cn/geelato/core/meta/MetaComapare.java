package cn.geelato.core.meta;

import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;

import java.util.*;

public class MetaComapare {
    private static Map<String, FieldMeta> indexFieldsByColumn(Collection<FieldMeta> fms) {
        Map<String, FieldMeta> map = new HashMap<>();
        if (fms == null) {
            return map;
        }
        for (FieldMeta fm : fms) {
            String key = fm.getColumnName();
            if (StringUtils.isBlank(key)) {
                key = fm.getFieldName();
            }
            if (StringUtils.isNotBlank(key)) {
                map.put(key, fm);
            }
        }
        return map;
    }

    public static Map<String, Object> compareEntitySources(MetaManager metaManager, String entityName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityName", entityName);
        EntityMeta classMeta = metaManager.getClassSourceEntity(entityName);
        EntityMeta dbMeta = metaManager.getDatabaseSourceEntity(entityName);
        result.put("existsInClass", classMeta != null);
        result.put("existsInDatabase", dbMeta != null);
        if (classMeta == null || dbMeta == null) {
            return result;
        }
        String classTable = classMeta.getTableName();
        String dbTable = dbMeta.getTableName();
        if (!Objects.equals(classTable, dbTable)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("class", classTable);
            diff.put("database", dbTable);
            result.put("tableNameDiff", diff);
        }
        String classTitle = classMeta.getEntityTitle();
        String dbTitle = dbMeta.getEntityTitle();
        if (!Objects.equals(classTitle, dbTitle)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("class", classTitle);
            diff.put("database", dbTitle);
            result.put("titleDiff", diff);
        }
        String classCatalog = classMeta.getCatalog();
        String dbCatalog = dbMeta.getCatalog();
        if (!Objects.equals(classCatalog, dbCatalog)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("class", classCatalog);
            diff.put("database", dbCatalog);
            result.put("catalogDiff", diff);
        }
        String classEntityType = classMeta.getEntityType() == null ? null : classMeta.getEntityType().name();
        String dbEntityType = dbMeta.getEntityType() == null ? null : dbMeta.getEntityType().name();
        if (!Objects.equals(classEntityType, dbEntityType)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("class", classEntityType);
            diff.put("database", dbEntityType);
            result.put("entityTypeDiff", diff);
        }
        String classTypeName = classMeta.getClassType() == null ? null : classMeta.getClassType().getName();
        String dbClassTypeName = dbMeta.getClassType() == null ? null : dbMeta.getClassType().getName();
        if (!Objects.equals(classTypeName, dbClassTypeName)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("class", classTypeName);
            diff.put("database", dbClassTypeName);
            result.put("classTypeDiff", diff);
        }
        String classTableMetaJson = classMeta.getTableMeta() == null ? null : JSON.toJSONString(classMeta.getTableMeta());
        String dbTableMetaJson = dbMeta.getTableMeta() == null ? null : JSON.toJSONString(dbMeta.getTableMeta());
        if (!Objects.equals(classTableMetaJson, dbTableMetaJson)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("class", classTableMetaJson);
            diff.put("database", dbTableMetaJson);
            result.put("tableMetaDiff", diff);
        }
        Map<String, FieldMeta> classFields = indexFieldsByColumn(classMeta.getFieldMetas());
        Map<String, FieldMeta> dbFields = indexFieldsByColumn(dbMeta.getFieldMetas());
        Set<String> all = new HashSet<>();
        all.addAll(classFields.keySet());
        all.addAll(dbFields.keySet());
        List<String> missingInClass = new ArrayList<>();
        List<String> missingInDatabase = new ArrayList<>();
        List<Map<String, Object>> typeDiffs = new ArrayList<>();
        List<Map<String, Object>> defaultDiffs = new ArrayList<>();
        List<Map<String, Object>> javaTypeDiffs = new ArrayList<>();
        List<Map<String, Object>> titleDiffs = new ArrayList<>();
        List<Map<String, Object>> enableStatusDiffs = new ArrayList<>();
        List<Map<String, Object>> delStatusDiffs = new ArrayList<>();
        for (String col : all) {
            FieldMeta cf = classFields.get(col);
            FieldMeta df = dbFields.get(col);
            if (cf == null) {
                missingInClass.add(col);
            }
            if (df == null) {
                missingInDatabase.add(col);
            }
            if (cf != null && df != null) {
                ColumnMeta ccm = cf.getColumnMeta();
                ColumnMeta dcm = df.getColumnMeta();
                String cType = ccm == null ? null : (StringUtils.isNotBlank(ccm.getType()) ? ccm.getType() : ccm.getDataType());
                String dType = dcm == null ? null : (StringUtils.isNotBlank(dcm.getType()) ? dcm.getType() : dcm.getDataType());
                if (!Objects.equals(cType, dType)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("column", col);
                    d.put("class", cType);
                    d.put("database", dType);
                    typeDiffs.add(d);
                }
                String cDef = ccm == null ? null : ccm.getDefaultValue();
                String dDef = dcm == null ? null : dcm.getDefaultValue();
                if (!Objects.equals(cDef, dDef)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("column", col);
                    d.put("class", cDef);
                    d.put("database", dDef);
                    defaultDiffs.add(d);
                }
                Integer cEnable = ccm == null ? null : ccm.getEnableStatus();
                Integer dEnable = dcm == null ? null : dcm.getEnableStatus();
                if (!Objects.equals(cEnable, dEnable)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("column", col);
                    d.put("class", cEnable);
                    d.put("database", dEnable);
                    enableStatusDiffs.add(d);
                }
                Integer cDel = ccm == null ? null : ccm.getDelStatus();
                Integer dDel = dcm == null ? null : dcm.getDelStatus();
                if (!Objects.equals(cDel, dDel)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("column", col);
                    d.put("class", cDel);
                    d.put("database", dDel);
                    delStatusDiffs.add(d);
                }
                String cTitle = cf.getTitle();
                String dTitle = df.getTitle();
                if (!Objects.equals(cTitle, dTitle)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("column", col);
                    d.put("class", cTitle);
                    d.put("database", dTitle);
                    titleDiffs.add(d);
                }
                Class<?> cj = cf.getFieldType();
                Class<?> dj = df.getFieldType();
                String cjName = cj == null ? null : cj.getName();
                String djName = dj == null ? null : dj.getName();
                if (!Objects.equals(cjName, djName)) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("field", cf.getFieldName());
                    d.put("class", cjName);
                    d.put("database", djName);
                    javaTypeDiffs.add(d);
                }
            }
        }
        result.put("missingColumnsInClass", missingInClass);
        result.put("missingColumnsInDatabase", missingInDatabase);
        result.put("columnTypeDiff", typeDiffs);
        result.put("columnDefaultDiff", defaultDiffs);
        result.put("javaTypeDiff", javaTypeDiffs);
        result.put("fieldTitleDiff", titleDiffs);
        result.put("columnEnableStatusDiff", enableStatusDiffs);
        result.put("columnDelStatusDiff", delStatusDiffs);
        return result;
    }

    private static void addDiff(List<Map<String, Object>> diffs, String path, Object classVal, Object dbVal, String type) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("path", path);
        diff.put("class", classVal);
        diff.put("database", dbVal);
        diff.put("type", type);
        diffs.add(diff);
    }

    private static String identifyKey(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.containsKey("columnName")) {
            return "columnName";
        }
        if (obj.containsKey("fieldName")) {
            return "fieldName";
        }
        if (obj.containsKey("view_name")) {
            return "view_name";
        }
        if (obj.containsKey("table_name")) {
            return "table_name";
        }
        if (obj.containsKey("name")) {
            return "name";
        }
        if (obj.containsKey("id")) {
            return "id";
        }
        if (obj.containsKey("code")) {
            return "code";
        }
        return null;
    }

    private static void deepCompareJsonObject(String path, JSONObject classJson, JSONObject dbJson, List<Map<String, Object>> diffs) {
        Set<String> keys = new HashSet<>();
        if (classJson != null) {
            keys.addAll(classJson.keySet());
        }
        if (dbJson != null) {
            keys.addAll(dbJson.keySet());
        }
        for (String key : keys) {
            Object cVal = classJson == null ? null : classJson.get(key);
            Object dVal = dbJson == null ? null : dbJson.get(key);
            String currentPath = Strings.isNotBlank(path) ? path + "." + key : key;
            if (cVal == null && dVal != null) {
                addDiff(diffs, currentPath, null, dVal, "missingInClass");
                continue;
            }
            if (cVal != null && dVal == null) {
                addDiff(diffs, currentPath, cVal, null, "missingInDatabase");
                continue;
            }
            if (cVal == null) {
                continue;
            }
            if (cVal instanceof JSONObject || dVal instanceof JSONObject) {
                deepCompareJsonObject(currentPath,
                        cVal instanceof JSONObject ? (JSONObject) cVal : null,
                        dVal instanceof JSONObject ? (JSONObject) dVal : null,
                        diffs);
            } else if (cVal instanceof JSONArray || dVal instanceof JSONArray) {
                deepCompareJsonArray(currentPath,
                        cVal instanceof JSONArray ? (JSONArray) cVal : null,
                        dVal instanceof JSONArray ? (JSONArray) dVal : null,
                        diffs);
            } else {
                if (!Objects.equals(cVal, dVal)) {
                    addDiff(diffs, currentPath, cVal, dVal, "valueDiff");
                }
            }
        }
    }

    private static void deepCompareJsonArray(String path, JSONArray classArr, JSONArray dbArr, List<Map<String, Object>> diffs) {
        int cSize = classArr == null ? 0 : classArr.size();
        int dSize = dbArr == null ? 0 : dbArr.size();
        if (cSize == 0 && dSize == 0) {
            return;
        }
        if (cSize == 0) {
            addDiff(diffs, path, null, dbArr, "arrayMissingInClass");
            return;
        }
        if (dSize == 0) {
            addDiff(diffs, path, classArr, null, "arrayMissingInDatabase");
            return;
        }
        boolean allJsonObjects = true;
        for (int i = 0; i < cSize; i++) {
            if (!(classArr.get(i) instanceof JSONObject)) {
                allJsonObjects = false;
                break;
            }
        }
        for (int i = 0; i < dSize; i++) {
            if (!(dbArr.get(i) instanceof JSONObject)) {
                allJsonObjects = false;
                break;
            }
        }
        if (allJsonObjects) {
            Map<String, JSONObject> cIndex = new LinkedHashMap<>();
            Map<String, JSONObject> dIndex = new LinkedHashMap<>();
            String keyName = null;
            if (classArr.get(0) instanceof JSONObject) {
                keyName = identifyKey((JSONObject) classArr.get(0));
            }
            if (Strings.isBlank(keyName) && dbArr.get(0) instanceof JSONObject) {
                keyName = identifyKey((JSONObject) dbArr.get(0));
            }
            if (Strings.isBlank(keyName)) {
                for (int i = 0; i < Math.max(cSize, dSize); i++) {
                    JSONObject cObj = i < cSize ? (JSONObject) classArr.get(i) : null;
                    JSONObject dObj = i < dSize ? (JSONObject) dbArr.get(i) : null;
                    deepCompareJsonObject(path + "[" + i + "]", cObj, dObj, diffs);
                }
                return;
            }
            for (int i = 0; i < cSize; i++) {
                JSONObject cObj = (JSONObject) classArr.get(i);
                Object key = cObj.get(keyName);
                if (key != null) {
                    cIndex.put(String.valueOf(key), cObj);
                }
            }
            for (int i = 0; i < dSize; i++) {
                JSONObject dObj = (JSONObject) dbArr.get(i);
                Object key = dObj.get(keyName);
                if (key != null) {
                    dIndex.put(String.valueOf(key), dObj);
                }
            }
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(cIndex.keySet());
            allKeys.addAll(dIndex.keySet());
            for (String k : allKeys) {
                JSONObject cObj = cIndex.get(k);
                JSONObject dObj = dIndex.get(k);
                if (cObj == null) {
                    addDiff(diffs, path + "[" + k + "]", null, dObj, "elementMissingInClass");
                } else if (dObj == null) {
                    addDiff(diffs, path + "[" + k + "]", cObj, null, "elementMissingInDatabase");
                } else {
                    deepCompareJsonObject(path + "[" + k + "]", cObj, dObj, diffs);
                }
            }
        } else {
            int max = Math.max(cSize, dSize);
            for (int i = 0; i < max; i++) {
                Object cVal = i < cSize ? classArr.get(i) : null;
                Object dVal = i < dSize ? dbArr.get(i) : null;
                String currentPath = path + "[" + i + "]";
                if (cVal == null && dVal != null) {
                    addDiff(diffs, currentPath, null, dVal, "elementMissingInClass");
                } else if (cVal != null && dVal == null) {
                    addDiff(diffs, currentPath, cVal, null, "elementMissingInDatabase");
                } else if (!Objects.equals(cVal, dVal)) {
                    addDiff(diffs, currentPath, cVal, dVal, "elementValueDiff");
                }
            }
        }
    }

    public static Map<String, Object> compareEntitySourcesAll(MetaManager metaManager, String entityName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityName", entityName);
        EntityMeta classMeta = metaManager.getClassSourceEntity(entityName);
        EntityMeta dbMeta = metaManager.getDatabaseSourceEntity(entityName);
        result.put("existsInClass", classMeta != null);
        result.put("existsInDatabase", dbMeta != null);
        if (classMeta == null || dbMeta == null) {
            return result;
        }
        JSONObject classJson = JSON.parseObject(JSON.toJSONString(classMeta));
        JSONObject dbJson = JSON.parseObject(JSON.toJSONString(dbMeta));
        List<Map<String, Object>> diffs = new ArrayList<>();
        deepCompareJsonObject("", classJson, dbJson, diffs);
        result.put("diffs", diffs);
        result.put("diffCount", diffs.size());
        return result;
    }

    private static String truncate(Object v, int len) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        if (s.length() <= len) {
            return s;
        }
        return s.substring(0, len) + "...";
    }

    public static void logDiffs(Logger log, Map<String, Object> result) {
        if (result == null) {
            return;
        }
        Object diffCountObj = result.get("diffCount");
        int diffCount = diffCountObj instanceof Number ? ((Number) diffCountObj).intValue() : 0;
        if (diffCount <= 0) {
            return;
        }
        String entityName = String.valueOf(result.get("entityName"));
        log.warn("========== META DIFF [{}] count={} ==========", entityName, diffCount);
        Object diffsObj = result.get("diffs");
        if (diffsObj instanceof List) {
            List<?> diffs = (List<?>) diffsObj;
            for (Object o : diffs) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                String path = String.valueOf(m.get("path"));
                String type = String.valueOf(m.get("type"));
                String c = truncate(m.get("class"), 200);
                String d = truncate(m.get("database"), 200);
                log.warn(" - [{}] {} | class={} | database={}", type, path, c, d);
            }
        }
        log.warn("========== END META DIFF [{}] ==========", entityName);
    }
}
