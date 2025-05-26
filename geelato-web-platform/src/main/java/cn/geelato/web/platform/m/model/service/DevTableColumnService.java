package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.*;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.column.ColumnSelectType;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.schema.SchemaColumn;
import cn.geelato.core.meta.schema.SchemaIndex;
import cn.geelato.web.platform.m.base.service.DbGenerateDynamicDao;
import cn.geelato.core.util.ClassUtils;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.arco.entity.SelectOptionData;
import cn.geelato.web.platform.m.arco.entity.SelectOptionGroup;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import cn.geelato.web.platform.m.model.utils.SchemaUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author diabl
 */
@Component
@Slf4j
public class DevTableColumnService extends BaseSortableService {
    private static final String DELETE_COMMENT_PREFIX = "已删除；";
    private static final String DELETE_DESCRIPTION_PREFIX = "Already removed; ";
    private static final String UPDATE_COMMENT_PREFIX = "已变更；";
    private static final String UPDATE_DESCRIPTION_PREFIX = "Already updated; ";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATETIME);
    @Lazy
    @Autowired
    protected DbGenerateDynamicDao dbGenerateDao;

    /**
     * 自动生成对应字段
     * <p>
     * 对于实体数据中的org和user字段，自动生成对应的id和name字段。
     *
     * @param model 实体数据对象，包含字段的元数据
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
     * 生成默认字段
     * <p>
     * 为数据库表生成默认的字段。
     *
     * @param tableMeta 表格元数据对象，包含表格的基本信息
     */
    public void createDefaultColumn(TableMeta tableMeta) {
        Assert.notNull(tableMeta, ApiErrorMsg.IS_NULL);
        if (Strings.isBlank(tableMeta.getId()) || Strings.isBlank(tableMeta.getEntityName())) {
            throw new RuntimeException(ApiErrorMsg.ID_IS_NULL);
        }
        if (TableTypeEnum.TABLE.getValue().equals(tableMeta.getTableType())) {
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
                    meta.setTableCatalog("def");
                    meta.setTableSchema(tableMeta.getTableSchema());
                    meta.setSynced(ColumnSyncedEnum.FALSE.getValue());
                    meta.setEncrypted(ColumnEncryptedEnum.FALSE.getValue());
                    createModel(meta);
                }
            }
        }
    }

    /**
     * 获取默认视图
     * <p>
     * 根据实体名称生成默认的视图SQL语句，并返回包含视图字段和视图构造SQL的参数映射。
     *
     * @param entityName 实体名称
     * @return 返回包含视图字段和视图构造SQL的参数映射
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
     * <p>
     * 根据传入的表格元数据对象，从数据库中查询对应的列信息，并根据是否需要删除所有现有列来更新dev_column表。
     *
     * @param tableMeta 表格元数据对象，包含表格的ID和实体名称等信息
     * @param deleteAll 是否删除所有现有列
     * @throws InvocationTargetException 如果在反射调用过程中发生异常，将抛出该异常
     * @throws IllegalAccessException    如果在反射调用过程中访问受限，将抛出该异常
     */
    public void resetTableColumnByDataBase(TableMeta tableMeta, boolean deleteAll) throws InvocationTargetException, IllegalAccessException {
        switchDbByConnectId(tableMeta.getConnectId());
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
                    meta.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
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
            List<SchemaIndex> schemaIndices = dbGenerateDao.dbQueryUniqueIndexesByTableName(tableMeta.getDbType(), tableMeta.getEntityName());
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
            List<Map<String, Object>> columnList = dbGenerateDao.dbQueryColumnList(tableMeta.getDbType(), tableMeta.getEntityName(), null);
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
     * 比较dev_column和数据库中字段，进行创建、更新、删除操作
     * <p>
     * 该方法用于比较传入的dev_column映射（metaMap）和数据库中的字段映射（schemaMap），并根据比较结果对字段进行相应的创建、更新或删除操作。
     *
     * @param tableMeta 表元数据对象，包含表的基本信息
     * @param metaMap   dev_column映射，键为字段名，值为对应的ColumnMeta对象
     * @param schemaMap 数据库字段映射，键为字段名，值为对应的SchemaColumn对象
     * @throws InvocationTargetException 如果在调用方法时发生异常
     * @throws IllegalAccessException    如果尝试访问不可访问的方法时抛出异常
     */
    private void compareHashMapKeys(TableMeta tableMeta, HashMap<String, ColumnMeta> metaMap, Map<String, SchemaColumn> schemaMap) throws InvocationTargetException, IllegalAccessException {
        // 遍历 metaMap 的键 不存在：删除
        for (String key : metaMap.keySet()) {
            if (!schemaMap.containsKey(key)) {
                ColumnMeta meta = metaMap.get(key);
                meta.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
                isDeleteModel(meta);
            }
        }
        // 遍历 schemaMap 的键 存在：更新；不存在：添加
        for (String key : schemaMap.keySet()) {
            SchemaColumn schema = schemaMap.get(key);
            ColumnMeta meta = schema.convertIntoMeta(metaMap.get(key));
            meta.setTableId(tableMeta.getId());
            meta.setTableName(tableMeta.dbTableName());
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
     * <p>
     * 对指定的列元数据进行逻辑删除操作，包括修改列名、注释、描述等信息，并更新列的状态。
     *
     * @param model 列元数据对象，包含列的详细信息
     * @throws InvocationTargetException 如果在反射调用过程中发生异常，将抛出该异常
     * @throws IllegalAccessException    如果在反射调用过程中访问受限，将抛出该异常
     */
    public void isDeleteModel(ColumnMeta model) throws InvocationTargetException, IllegalAccessException {
        TableMeta tableMeta = getModel(TableMeta.class, model.getTableId());
        switchDbByConnectId(tableMeta.getConnectId());
        // 重命名
        String newColumnName = String.format("%s_d%s", model.getName(), System.currentTimeMillis());
        String newTitle = DELETE_COMMENT_PREFIX + model.getTitle();
        String newComment = DELETE_COMMENT_PREFIX + (Strings.isNotBlank(model.getComment()) ? model.getComment() : model.getTitle());
        // delete 2023-06-25 13:14:15 用户[user]=>[user_2023...]。
        String newDescription = String.format("delete %s %s[%s]=>[%s]。\n", sdf.format(new Date()), model.getTitle(), model.getName(), newColumnName) + model.getDescription();
        // 常用
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        model.setDelStatus(DeleteStatusEnum.IS.getValue());
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
        sqlParams.put("connectId", tableMeta.getConnectId());
        // 数据库表中是否有该字段
        List<Map<String, Object>> columnList = dbGenerateDao.dbQueryColumnList(tableMeta.getDbType(), model.getTableName(), model.getName());
        boolean isColumn = columnList != null && !columnList.isEmpty();
        // 修正：外键
        if (isColumn) {
            dynamicDao.execute(tableMeta.getDbType() + "_renameColumn", sqlParams);
        }
        dao.execute("upgradeMetaAfterDelColumn", sqlParams);
        model.setName(newColumnName);
        dao.save(model);
    }

    /**
     * 分组选择 select
     * <p>
     * 根据提供的选择类型列表，生成分组的选择选项。
     *
     * @param selectTypes 选择类型列表，包含每个选择类型的信息
     * @return 返回包含分组选择选项的列表
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
     * 选择项数据获取
     * <p>
     * 根据传入的选择类型列表，获取对应的选择项数据列表。
     *
     * @param selectTypes 选择类型列表
     * @return 返回包含选择项数据的列表
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
     * 解析 ColumnSelectType 列表
     * <p>
     * 将传入的 ColumnSelectType 列表解析为按组分类的选择项数据映射。
     *
     * @param selectTypes ColumnSelectType 列表，包含每个选择项的类型信息
     * @return 返回按组分类的选择项数据映射，键为组名，值为对应组内的选择项数据列表
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

    /**
     * 更新表格字段
     * <p>
     * 根据传入的表单字段（form）和模型字段（model），对表格字段进行更新操作。
     *
     * @param form  表单字段对象，包含表单字段的当前信息
     * @param model 模型字段对象，包含模型字段的新信息
     * @return 返回更新后的表单字段对象
     * @throws InvocationTargetException 如果在反射调用过程中发生异常，将抛出该异常
     * @throws IllegalAccessException    如果在反射调用过程中访问受限，将抛出该异常
     */
    public ColumnMeta upgradeTable(TableMeta tableMeta, ColumnMeta form, ColumnMeta model) throws InvocationTargetException, IllegalAccessException {
        switchDbByConnectId(tableMeta.getConnectId());
        // 字段标识，是否变更
        if (model.getName().equals(form.getName())) {
            form.setSynced(ColumnSyncedEnum.FALSE.getValue());
            return form;
        }
        form.setSynced(ColumnSyncedEnum.TRUE.getValue());
        form.setComment(Strings.isNotBlank(form.getComment()) ? form.getComment() : form.getTitle());
        // 复制字段
        model.setId(null);
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        model.setDelStatus(DeleteStatusEnum.IS.getValue());
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
        changeParams.putAll(getSqlParams(form, ""));
        changeParams.put("name", model.getName());
        changeParams.put("newName", form.getName());
        changeParams.put("connectId", tableMeta.getConnectId());
        // 数据库表中是否有该字段
        List<Map<String, Object>> columnList = dbGenerateDao.dbQueryColumnList(tableMeta.getDbType(), model.getTableName(), model.getName());
        boolean isColumn = columnList != null && !columnList.isEmpty();
        if (isColumn) {
            dynamicDao.execute(tableMeta.getDbType() + "_renameColumn", changeParams);
        }
        dao.execute("upgradeMetaAfterUpdateColumn", changeParams);

        return form;
    }

    /**
     * 获取 SQL 参数
     * <p>
     * 根据提供的列元数据对象和前缀，生成包含列元数据信息的 SQL 参数映射。
     *
     * @param model  列元数据对象，包含列的详细信息
     * @param prefix 参数名前缀
     * @return 返回包含列元数据信息的 SQL 参数映射
     * @throws InvocationTargetException 如果在反射调用过程中发生异常，将抛出该异常
     * @throws IllegalAccessException    如果在反射调用过程中访问受限，将抛出该异常
     */
    public Map<String, Object> getSqlParams(ColumnMeta model, String prefix) throws InvocationTargetException, IllegalAccessException {
        Map<String, Object> maps = JSONObject.parseObject(JSONObject.toJSONString(model));
        Map<String, Object> modelMaps = new HashMap<>();
        for (Map.Entry map : maps.entrySet()) {
            modelMaps.put(prefix + map.getKey(), map.getValue());
        }
        return modelMaps;
    }

}
