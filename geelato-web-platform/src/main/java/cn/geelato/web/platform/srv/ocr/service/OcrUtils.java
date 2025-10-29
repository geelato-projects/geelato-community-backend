package cn.geelato.web.platform.srv.ocr.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.meta.Entity;
import cn.geelato.meta.Dict;
import cn.geelato.meta.DictItem;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.ocr.enums.DictDisposeEnum;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class OcrUtils {
    private static final MetaManager metaManager = MetaManager.singleInstance();

    /**
     * 从给定内容中提取与正则表达式匹配的子串
     *
     * @param str   待处理的内容字符串
     * @param regex 用于匹配内容的正则表达式
     * @return 包含所有匹配子串的字符串，如果没有匹配项则返回空字符串
     */
    public static String extract(String str, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(str);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            result.append(matcher.group());
        }
        return result.toString();
    }

    /**
     * 移除字符串末尾的换行符（包括"\r\n"、"\n"或"\r"）
     *
     * @param str 待处理的字符串
     * @return 移除换行符后的字符串
     */
    public static String removeLf(String str) {
        if (str != null) {
            if (str.endsWith("\r\n")) {
                str = str.substring(0, str.length() - 2);
            } else if (str.endsWith("\n")) {
                str = str.substring(0, str.length() - 1);
            } else if (str.endsWith("\r")) {
                str = str.substring(0, str.length() - 1);
            }
        }

        return str;
    }

    /**
     * 根据字典编码查询字典项列表
     *
     * @param dictCode 字典编码
     * @return 返回与给定字典编码匹配的字典项列表，如果未找到匹配的字典项，则返回null
     */
    private static List<DictItem> queryDictItemsByDictCode(String dictCode) {
        FilterGroup filter1 = new FilterGroup();
        filter1.addFilter("dictCode", FilterGroup.Operator.in, dictCode);
        filter1.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
        List<Dict> dicts = getDao(null).queryList(Dict.class, filter1, "update_at DESC");
        if (dicts != null && !dicts.isEmpty()) {
            FilterGroup filter2 = new FilterGroup();
            filter2.addFilter("dictId", FilterGroup.Operator.in, dicts.get(0).getId());
            filter2.addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            return getDao(null).queryList(DictItem.class, filter2, null);
        }
        return null;
    }

    /**
     * 根据字典编码和字典项名称计算字典项编码
     *
     * @param content  字典项名称
     * @param dictCode 字典编码
     * @param type     处理规则，例如"CONTAINS"表示包含关系，"EQUALS"表示相等
     * @return 如果找到匹配的字典项，则返回其字典项编码；否则返回null
     */
    public static String calculateItemCode(String content, String dictCode, String type) {
        if (Strings.isNotBlank(content)) {
            // 获取字典项
            List<DictItem> dictItemList = queryDictItemsByDictCode(dictCode);
            // 比对
            if (dictItemList != null && !dictItemList.isEmpty()) {
                DictDisposeEnum disposeEnum = DictDisposeEnum.lookUp(type);
                if (disposeEnum != null) {
                    return disposeEnum.dispose(content, dictItemList);
                } else {
                    throw new RuntimeException("暂不支持该处理规则");
                }
            }
        }
        return null;
    }

    /**
     * 根据给定的内容和字典映射字符串，计算并返回对应的值。
     *
     * @param content    需要查找的内容
     * @param dictMapStr 包含字典映射关系的JSON字符串
     * @param type       处理规则，例如"CONTAINS"表示包含关系，"EQUALS"表示相等
     * @return 如果找到对应的内容，则返回对应的值；否则返回null
     * @throws RuntimeException 如果字典映射字符串格式不正确，则抛出运行时异常
     */
    public static String calculateRadio(String content, String dictMapStr, String type) {
        if (Strings.isNotBlank(content)) {
            try {
                Map<String, String> dictMap = JSON.parseObject(dictMapStr, Map.class);
                if (dictMap != null && !dictMap.isEmpty()) {
                    DictDisposeEnum disposeEnum = DictDisposeEnum.lookUp(type);
                    if (disposeEnum != null) {
                        return disposeEnum.dispose(content, dictMap);
                    } else {
                        throw new RuntimeException("暂不支持该处理规则");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("The data dictionary format is incorrect", e);
            }
        }
        return null;
    }

    /**
     * 根据字典编码和多个字典项名称计算对应的字典项编码
     *
     * @param content  字典项名称列表，以逗号分隔
     * @param dictCode 字典编码
     * @param type     处理规则，例如"CONTAINS"表示包含关系，"EQUALS"表示相等
     * @return 如果找到匹配的字典项，则返回其字典项编码列表，以逗号分隔；否则返回null
     */
    public static String calculateItemCodes(String content, String dictCode, String type) {
        List<String> names = StringUtils.toList(content);
        if (names.isEmpty()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        // 获取字典项
        List<DictItem> dictItemList = queryDictItemsByDictCode(dictCode);
        // 比对
        if (dictItemList != null && !dictItemList.isEmpty()) {
            DictDisposeEnum disposeEnum = DictDisposeEnum.lookUp(type);
            if (disposeEnum != null) {
                codes = disposeEnum.dispose(names, dictItemList);
            } else {
                throw new RuntimeException("暂不支持该处理规则");
            }
        }
        return !codes.isEmpty() ? String.join(",", codes) : null;
    }

    /**
     * 根据给定的规则和目标列名，从数据库中查询并返回目标列的值
     *
     * @param value 字段值
     * @param rule  规则字符串，格式为"表名:列名1,列名2..."
     * @param goal  目标列名
     * @return 查询结果，如果查询成功则返回目标列的值，否则返回null
     */
    public static String calculateTables(String value, String rule, String goal) {
        Map<String, Object> map = parseRuleMap(rule, goal);
        if (Strings.isBlank(value) || map == null) {
            return null;
        }
        String tableName = map.get("tableName") == null ? "" : map.get("tableName").toString();
        List<String> columnNames = map.get("query") == null ? null : (List<String>) map.get("query");
        String goalColumnName = map.get("goal") == null ? null : map.get("goal").toString();
        if (Strings.isBlank(tableName) || columnNames == null || columnNames.isEmpty() || goalColumnName == null) {
            return null;
        }
        boolean hasDel = map.get("hasDel") != null && Boolean.parseBoolean(map.get("hasDel").toString());

        StringBuilder sql = new StringBuilder();
        sql.append("select ").append(goalColumnName).append(" from ").append(tableName).append(" where ");
        if (hasDel) {
            sql.append(" del_status = 0 and ");
        }
        sql.append("(");
        for (int i = 0; i < columnNames.size(); i++) {
            sql.append(columnNames.get(i)).append(" = '").append(value).append("'");
            if (i != columnNames.size() - 1) {
                sql.append(" or ");
            }
        }
        sql.append(")");
        try {
            List<Map<String, Object>> list = getDao(tableName).getJdbcTemplate().queryForList(sql.toString());
            if (list != null && list.size() > 0) {
                Object obj = list.get(0).get(goalColumnName);
                return obj != null ? obj.toString() : null;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    /**
     * 解析规则字符串并生成包含规则信息的映射表
     *
     * @param rule 规则字符串，格式为"表名:列名1,列名2..."
     * @param goal 目标列名
     * @return 包含规则信息的映射表，如果解析失败则返回null
     */
    private static Map<String, Object> parseRuleMap(String rule, String goal) {
        // 解析规则
        Map<String, Object> map = new HashMap<>();
        String tableName = null;
        List<String> columnNames = new ArrayList<>();
        if (rule.contains(":")) {
            String[] arr = rule.split(":");
            if (arr.length == 2) {
                tableName = arr[0];
                columnNames = StringUtils.toListDr(arr[1]);
            }
        }
        if (Strings.isBlank(tableName) || columnNames.isEmpty()) {
            return null;
        }
        // 校验表名
        EntityMeta entityMeta = metaManager.getByEntityName(tableName);
        if (entityMeta == null) {
            return null;
        }
        map.put("tableName", tableName);
        // 校验列名
        Map<String, ColumnMeta> columnMap = getColumnMap(entityMeta);
        if (!columnMap.containsKey(goal)) {
            return null;
        }
        map.put("goal", columnMap.get(goal).getName());
        // 校验列名
        List<String> querys = new ArrayList<>();
        for (String columnName : columnNames) {
            if (!columnMap.containsKey(columnName)) {
                return null;
            }
            querys.add(columnMap.get(columnName).getName());
        }
        map.put("query", querys);
        map.put("hasDel", columnMap.containsKey("delStatus"));
        return map;
    }


    /**
     * 根据实体元数据生成列名映射
     *
     * @param entityMeta 实体元数据对象
     * @return 包含字段名和列元数据的映射表
     */
    private static Map<String, ColumnMeta> getColumnMap(EntityMeta entityMeta) {
        Collection<FieldMeta> fieldMetas = entityMeta.getFieldMetas();
        Map<String, ColumnMeta> columnMap = new HashMap<>();
        for (FieldMeta fieldMeta : fieldMetas) {
            if (fieldMeta.getColumnMeta() != null) {
                columnMap.put(fieldMeta.getFieldName(), fieldMeta.getColumnMeta());
            }
        }
        return columnMap;
    }

    private static Dao getDao(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            Class<?> entityClass = TableMeta.class;
            tableName = Optional.ofNullable(entityClass.getAnnotation(Entity.class))
                    .map(Entity::name)
                    .filter(name -> !name.isEmpty())
                    .orElseGet(entityClass::getSimpleName);
        }
        EntityMeta entityMeta = MetaManager.singleInstance().getByEntityName(tableName);
        if (entityMeta == null || entityMeta.getTableMeta() == null || org.apache.commons.lang3.StringUtils.isBlank(entityMeta.getTableMeta().getConnectId())) {
            throw new RuntimeException("The model does not exist in memory");
        }
        DataSource ds = DataSourceManager.singleInstance().getDataSource(entityMeta.getTableMeta().getConnectId());
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }
}
