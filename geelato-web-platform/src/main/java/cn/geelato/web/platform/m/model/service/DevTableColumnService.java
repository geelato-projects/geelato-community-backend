package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.*;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.schema.SchemaColumn;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.core.util.ClassUtils;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.SchemaUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.arco.select.SelectOptionData;
import cn.geelato.web.platform.arco.select.SelectOptionGroup;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author diabl
 */
@Component
public class DevTableColumnService extends BaseSortableService {
    private static final String DELETE_COMMENT_PREFIX = "已删除；";
    private static final String DELETE_DESCRIPTION_PREFIX = "Already removed; ";
    private static final String UPDATE_COMMENT_PREFIX = "已变更；";
    private static final String UPDATE_DESCRIPTION_PREFIX = "Already updated; ";
    private static final Logger logger = LoggerFactory.getLogger(DevTableColumnService.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATETIME);

    /**
     * 自动生成对于字段
     * org、user 需要 id、name
     *
     * @param model 实体数据
     * @return
     */
    public void automaticGeneration(ColumnMeta model) {
        Assert.notNull(model, ApiErrorMsg.IS_NULL);
        if (!model.isAutoAdd() || Strings.isBlank(model.getAutoName())) {
            return;
        }
        // 校验是否已经存在
        Map<String, Object> params = new HashMap<>();
        params.put("name", model.getAutoName());
        params.put("tableId", model.getTableId());
        params.put("tableName", model.getTableName());
        List<ColumnMeta> metaList = queryModel(ColumnMeta.class, params);
        if (metaList != null && !metaList.isEmpty()) {
            ColumnMeta autoMeta = metaList.get(0);
            autoMeta.setAutoAdd(true);
            autoMeta.setAutoName(model.getName());
            updateModel(autoMeta);
            return;
        }
        // 创建
        String modelAutoName = model.getName();
        model.setId(null);
        model.setName(model.getAutoName());
        model.setFieldName(StringUtils.toCamelCase(model.getAutoName()));
        model.setOrdinalPosition(model.getOrdinalPosition() + 1);
        model.setDefaultValue(null);
        model.setKey(false);
        model.setUniqued(false);
        model.setAutoAdd(true);
        model.setAutoName(modelAutoName);
        model.afterSet();
        createModel(model);
    }

    /**
     * 生成默认字段，表格类型：数据库表
     *
     * @param tableMeta
     */
    public void createDefaultColumn(TableMeta tableMeta) {
        Assert.notNull(tableMeta, ApiErrorMsg.IS_NULL);
        if (Strings.isBlank(tableMeta.getId()) || Strings.isBlank(tableMeta.getEntityName())) {
            throw new RuntimeException(ApiErrorMsg.ID_IS_NULL);
        }
        if (TableTypeEnum.TABLE.getCode().equals(tableMeta.getTableType())) {
            List<ColumnMeta> metaList = MetaManager.singleInstance().getDefaultColumn();
            if (metaList != null && !metaList.isEmpty()) {
                // 排序
                metaList.sort(new Comparator<ColumnMeta>() {
                    @Override
                    public int compare(ColumnMeta o1, ColumnMeta o2) {
                        return o1.getOrdinalPosition() - o2.getOrdinalPosition();
                    }
                });
                // 创建
                for (ColumnMeta meta : metaList) {
                    meta.setAppId(tableMeta.getAppId());
                    meta.setTenantCode(tableMeta.getTenantCode());
                    meta.setTableId(tableMeta.getId());
                    meta.setTableName(tableMeta.getEntityName());
                    meta.setTableCatalog(null);
                    meta.setTableSchema(null);
                    meta.setSynced(ColumnSyncedEnum.FALSE.getValue());
                    meta.setEncrypted(ColumnEncryptedEnum.FALSE.getValue());
                    createModel(meta);
                }
            }
        }
    }

    /**
     * 获取默认视图
     *
     * @param entityName
     * @return
     */
    public Map<String, Object> getDefaultViewSql(String entityName) {
        Map<String, Object> viewParams = new HashMap<>();
        if (Strings.isBlank(entityName)) {
            return viewParams;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("tableName", entityName);
        List<ColumnMeta> columnMetaList = queryModel(ColumnMeta.class, params);
        if (columnMetaList != null && !columnMetaList.isEmpty()) {
            // 排序
            columnMetaList.sort(new Comparator<ColumnMeta>() {
                @Override
                public int compare(ColumnMeta o1, ColumnMeta o2) {
                    return o1.getOrdinalPosition() - o2.getOrdinalPosition();
                }
            });
            // 去重
            HashMap<String, ColumnMeta> columnMetaMap = new HashMap<String, ColumnMeta>();
            for (ColumnMeta columnMeta : columnMetaList) {
                if (Strings.isNotBlank(columnMeta.getName()) && !columnMetaMap.containsKey(columnMeta.getName())) {
                    columnMetaMap.put(columnMeta.getName(), columnMeta);
                }
            }
            // 拼接
            List<Object> viewColumns = new ArrayList<>();
            if (!columnMetaMap.isEmpty()) {
                List<String> asList = new ArrayList<>();
                for (Map.Entry<String, ColumnMeta> metaMap : columnMetaMap.entrySet()) {
                    ColumnMeta meta = metaMap.getValue();
                    asList.add(meta.getName());
                    // 保存视图字段
                    meta.afterSet();
                    viewColumns.add(ClassUtils.toMapperDBObject(meta));
                }
                viewParams.put("viewColumns", JSON.toJSONString(viewColumns));
                viewParams.put("viewConstruct", String.format(MetaDaoSql.SQL_TABLE_DEFAULT_VIEW, String.join(",", asList), entityName));
                return viewParams;
            }
        }
        viewParams.put("viewConstruct", String.format(MetaDaoSql.SQL_TABLE_DEFAULT_VIEW, "*", entityName));

        return viewParams;
    }

    /**
     * 依据表格情况，从数据库中更新至 dev_column 中
     *
     * @param tableMeta
     * @param deleteAll
     */
    public void resetTableColumnByDataBase(TableMeta tableMeta, boolean deleteAll) throws InvocationTargetException, IllegalAccessException {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("tableId", tableMeta.getId());
        queryParams.put("tableName", tableMeta.getEntityName());
        queryParams.put("tableSchema", null);
        // dev_table
        List<ColumnMeta> columnMetas = queryModel(ColumnMeta.class, queryParams);
        HashMap<String, ColumnMeta> columnMetaMap = new HashMap<>();
        if (columnMetas != null && columnMetas.size() > 0) {
            for (ColumnMeta meta : columnMetas) {
                if (deleteAll) {
                    meta.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
                    isDeleteModel(meta);
                    continue;
                }
                if (Strings.isNotBlank(meta.getName()) && !columnMetaMap.containsKey(meta.getName())) {
                    columnMetaMap.put(meta.getName(), meta);
                }
            }
        }
        if (!deleteAll) {
            // database_table_index
            String indexSql = String.format(MetaDaoSql.SQL_INDEXES_NO_PRIMARY, tableMeta.getEntityName());
            logger.info(indexSql);
            List<Map<String, Object>> indexList = dao.getJdbcTemplate().queryForList(indexSql);
            List<SchemaIndex> schemaIndices = SchemaUtils.buildData(SchemaIndex.class, indexList);
            List<String> uniques = new ArrayList<>();
            if (schemaIndices != null && schemaIndices.size() > 0) {
                for (SchemaIndex index : schemaIndices) {
                    boolean isUnique = !Strings.isBlank(index.getNonUnique()) && !Boolean.parseBoolean(index.getNonUnique());
                    if (Strings.isNotBlank(index.getColumnName()) && isUnique) {
                        uniques.add(index.getColumnName());
                    }
                }
            }
            // database_table_column
            String columnSql = String.format(MetaDaoSql.INFORMATION_SCHEMA_COLUMNS, MetaDaoSql.TABLE_SCHEMA_METHOD, " AND TABLE_NAME='" + tableMeta.getEntityName() + "'");
            logger.info(columnSql);
            List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(columnSql);
            List<SchemaColumn> schemaColumns = SchemaUtils.buildData(SchemaColumn.class, columnList);
            HashMap<String, SchemaColumn> schemaColumnMap = new HashMap<>();
            if (schemaColumns != null && schemaColumns.size() > 0) {
                for (SchemaColumn schema : schemaColumns) {
                    if (Strings.isNotBlank(schema.getColumnName()) && !schemaColumnMap.containsKey(schema.getColumnName())) {
                        schema.setUnique(uniques.contains(schema.getColumnName()));
                        schemaColumnMap.put(schema.getColumnName(), schema);
                    }
                }
            }
            compareHashMapKeys(tableMeta, columnMetaMap, schemaColumnMap);
        }
    }

    /**
     * 比较 dev_column 和 数据库中字段 创建、更新、删除
     *
     * @param metaMap
     * @param schemaMap
     */
    private void compareHashMapKeys(TableMeta tableMeta, HashMap<String, ColumnMeta> metaMap, Map<String, SchemaColumn> schemaMap) throws InvocationTargetException, IllegalAccessException {
        // 遍历 metaMap 的键 不存在：删除
        for (String key : metaMap.keySet()) {
            if (!schemaMap.containsKey(key)) {
                ColumnMeta meta = metaMap.get(key);
                meta.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
                isDeleteModel(meta);
            }
        }
        // 遍历 schemaMap 的键 存在：更新；不存在：添加
        for (String key : schemaMap.keySet()) {
            SchemaColumn schema = schemaMap.get(key);
            ColumnMeta meta = schema.convertIntoMeta(metaMap.get(key));
            meta.setTableId(tableMeta.getId());
            meta.setTableName(tableMeta.getTableName());
            if (!metaMap.containsKey(key)) {
                meta.setName(schema.getColumnName().toLowerCase(Locale.ENGLISH));
                meta.setFieldName(StringUtils.toCamelCase(meta.getName()));
                createModel(meta);
            } else {
                updateModel(meta);
            }
        }
    }

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(ColumnMeta model) throws InvocationTargetException, IllegalAccessException {
        // 重命名
        String newColumnName = String.format("%s_d%s", model.getName(), System.currentTimeMillis());
        String newTitle = DELETE_COMMENT_PREFIX + model.getTitle();
        String newComment = DELETE_COMMENT_PREFIX + (Strings.isNotBlank(model.getComment()) ? model.getComment() : model.getTitle());
        // delete 2023-06-25 13:14:15 用户[user]=>[user_2023...]。
        String newDescription = String.format("delete %s %s[%s]=>[%s]。\n", sdf.format(new Date()), model.getTitle(), model.getName(), newColumnName) + model.getDescription();
        // 常用
        model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        model.setDelStatus(DeleteStatusEnum.IS.getCode());
        model.setDeleteAt(new Date());
        model.setSeqNo(ColumnDefault.SEQ_NO_DELETE);
        // 标记
        model.setTitle(newTitle);
        model.setComment(newComment);
        model.setDescription(newDescription);
        // 去除 主键、必填、唯一约束
        model.setKey(false);
        model.setNullable(true);
        model.setUniqued(false);
        model.afterSet();

        Map<String, Object> sqlParams = new HashMap<>();
        sqlParams.put("remark", "delete column. \n");
        sqlParams.put("newName", newColumnName);
        sqlParams.putAll(getSqlParams(model, ""));
        sqlParams.put("deleteAt", sdf.format(new Date()));
        // 数据库表中是否有该字段
        String filterSql = String.format(" AND TABLE_NAME='%s' AND COLUMN_NAME='%s' ", model.getTableName(), model.getName());
        String columnSql = String.format(MetaDaoSql.INFORMATION_SCHEMA_COLUMNS, MetaDaoSql.TABLE_SCHEMA_METHOD, filterSql);
        logger.info(columnSql);
        List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(columnSql);
        if (columnList == null || columnList.isEmpty()) {
            sqlParams.put("isColumn", false);
        } else {
            sqlParams.put("isColumn", true);
        }
        // 修正：外键
        dao.execute("metaDeleteColumn", sqlParams);

        model.setName(newColumnName);
        dao.save(model);
    }

    /**
     * 分组选择 select
     *
     * @param selectTypes
     * @return
     */
    public List<SelectOptionGroup> getSelectOptionGroup(List<ColumnSelectType> selectTypes) {
        List<SelectOptionGroup> groups = new ArrayList<>();
        HashMap<String, List<SelectOptionData<ColumnSelectType>>> optionDataMap = getSelectOptionDataMap(selectTypes);
        if (!optionDataMap.isEmpty()) {
            for (Map.Entry map : optionDataMap.entrySet()) {
                SelectOptionGroup group = new SelectOptionGroup();
                group.setLabel((String) map.getKey());
                group.setOptions(((List<SelectOptionData>) map.getValue()).toArray(new SelectOptionData[0]));
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * 选择 select
     *
     * @param selectTypes
     * @return
     */
    public List<SelectOptionData<ColumnSelectType>> getSelectOptionData(List<ColumnSelectType> selectTypes) {
        List<SelectOptionData<ColumnSelectType>> selects = new ArrayList<>();
        HashMap<String, List<SelectOptionData<ColumnSelectType>>> optionDataMap = getSelectOptionDataMap(selectTypes);
        if (!optionDataMap.isEmpty()) {
            for (Map.Entry map : optionDataMap.entrySet()) {
                selects.addAll((List<SelectOptionData<ColumnSelectType>>) map.getValue());
            }
        }

        return selects;
    }

    /**
     * 解析 ColumnSelectType
     *
     * @param selectTypes
     * @return
     */
    private HashMap<String, List<SelectOptionData<ColumnSelectType>>> getSelectOptionDataMap(List<ColumnSelectType> selectTypes) {
        HashMap<String, List<SelectOptionData<ColumnSelectType>>> stringListMap = new HashMap<>();

        if (selectTypes != null && !selectTypes.isEmpty()) {
            // 设置分组
            for (ColumnSelectType st1 : selectTypes) {
                if (Strings.isNotBlank(st1.getGroup()) && !stringListMap.containsKey(st1.getGroup())) {
                    // 实际选项
                    List<SelectOptionData<ColumnSelectType>> optionDatas = new ArrayList<>();
                    for (ColumnSelectType st2 : selectTypes) {
                        if (st1.getGroup().equals(st2.getGroup())) {
                            SelectOptionData optionData = new SelectOptionData();
                            optionData.setLabel(st2.getLabel());
                            optionData.setValue(st2.getValue());
                            optionData.setDisabled(st2.getDisabled());
                            optionData.setData(st2);
                            optionDatas.add(optionData);
                        }
                    }
                    stringListMap.put(st1.getGroup(), optionDatas);
                }
            }
        }
        return stringListMap;
    }

    public ColumnMeta upgradeTable(ColumnMeta form, ColumnMeta model) throws InvocationTargetException, IllegalAccessException {
        // 字段标识，是否变更
        if (model.getName().equals(form.getName())) {
            form.setSynced(ColumnSyncedEnum.FALSE.getValue());
            return form;
        }
        form.setSynced(ColumnSyncedEnum.TRUE.getValue());
        // 复制字段
        model.setId(null);
        model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        model.setDelStatus(DeleteStatusEnum.IS.getCode());
        model.setDeleteAt(new Date());
        model.setSeqNo(ColumnDefault.SEQ_NO_DELETE);
        // 标记
        model.setTitle(UPDATE_COMMENT_PREFIX + model.getTitle());
        model.setComment(UPDATE_COMMENT_PREFIX + model.getComment());
        model.setDescription(String.format("update %s to %s. \n", sdf.format(new Date()), form.getName()) + model.getDescription());
        // 去除 主键、必填、唯一约束
        model.setKey(false);
        model.setNullable(true);
        model.setUniqued(false);
        model.afterSet();
        // 数据库表直接修改
        Map<String, Object> changeParams = new HashMap<>();
        changeParams.putAll(getSqlParams(model, "model"));
        changeParams.putAll(getSqlParams(form, "form"));
        // 不进行字段复制
        changeParams.put("isCopy", false);
        // 数据库表中是否有该字段
        String filterSql = String.format(" AND TABLE_NAME='%s' AND COLUMN_NAME='%s' ", model.getTableName(), model.getName());
        String columnSql = String.format(MetaDaoSql.INFORMATION_SCHEMA_COLUMNS, MetaDaoSql.TABLE_SCHEMA_METHOD, filterSql);
        logger.info(columnSql);
        List<Map<String, Object>> columnList = dao.getJdbcTemplate().queryForList(columnSql);
        if (columnList == null || columnList.isEmpty()) {
            changeParams.put("isColumn", false);
        } else {
            form.setComment(Strings.isNotBlank(form.getComment()) ? form.getComment() : form.getTitle());
            changeParams.put("isColumn", true);
        }
        dao.execute("metaResetColumn", changeParams);
        // dao.save(model);

        return form;
    }

    public Map<String, Object> getSqlParams(ColumnMeta model, String prefix) throws InvocationTargetException, IllegalAccessException {
        Map<String, Object> maps = JSONObject.parseObject(JSONObject.toJSONString(model));
        Map<String, Object> modelMaps = new HashMap<>();
        for (Map.Entry map : maps.entrySet()) {
            modelMaps.put(prefix + map.getKey(), map.getValue());
        }
        return modelMaps;
    }

}
