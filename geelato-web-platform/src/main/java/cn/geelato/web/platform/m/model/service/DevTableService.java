package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.*;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.schema.SchemaTable;
import cn.geelato.core.orm.DbGenerateDao;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import cn.geelato.web.platform.m.model.utils.SchemaUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
@Slf4j
public class DevTableService extends BaseSortableService {
    private static final String DELETE_COMMENT_PREFIX = "已删除；";
    private static final String UPDATE_COMMENT_PREFIX = "已变更；";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATETIME);
    @Lazy
    @Qualifier("dbGenerateDao")
    protected DbGenerateDao dbGenerateDao;
    @Lazy
    @Autowired
    private DevDbConnectService devDbConnectService;
    @Lazy
    @Autowired
    private DevTableColumnService devTableColumnService;
    @Lazy
    @Autowired
    private DevTableForeignService devTableForeignService;

    /**
     * 设置TableMeta对象后的处理
     *
     * @param form TableMeta对象
     * @throws RuntimeException 当表id为空或连接不存在时抛出异常
     */
    public void afterSet(TableMeta form) {
        // 判断表id是否为空
        if (StringUtils.isBlank(form.getConnectId())) {
            throw new RuntimeException("connect id can not be null");
        }
        ConnectMeta connectMeta = getModel(ConnectMeta.class, form.getConnectId());
        if (connectMeta == null) {
            throw new RuntimeException("connect not exist");
        }
        form.setTableSchema(connectMeta.getDbSchema());
        if (Strings.isBlank(form.getDbType())) {
            form.setDbType(connectMeta.getDbType());
        }
    }

    /**
     * 全量查询
     * <p>
     * 根据提供的实体类和条件参数，执行全量查询操作，返回符合条件的记录列表。
     *
     * @param entity 查询实体类，指定查询结果的数据类型
     * @param params 条件参数，包含查询所需的过滤条件
     * @param <T>    泛型参数，表示查询结果的数据类型
     * @return 返回符合条件的记录列表
     */
    @Override
    public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
        dao.setDefaultFilter(true, filterGroup);
        return dao.queryList(entity, params, "entity_name ASC");
    }

    /**
     * 从数据库同步至模型
     * <p>
     * 根据传入的表格元数据对象，从数据库中查询对应表格的信息，并将其同步至模型中。
     *
     * @param tableMeta 表格元数据对象，包含表格的基本信息
     * @throws ParseException            如果在解析日期时发生错误，则抛出该异常
     * @throws InvocationTargetException 如果在反射调用过程中发生异常，则抛出该异常
     * @throws IllegalAccessException    如果在反射调用过程中访问受限，则抛出该异常
     */
    public void resetTableByDataBase(TableMeta tableMeta) throws ParseException, InvocationTargetException, IllegalAccessException {
        switchDbByConnectId(tableMeta.getConnectId());
        // database_table
        List<Map<String, Object>> tableList = dbGenerateDao.dbQueryAllTables(tableMeta.getDbType(), tableMeta.dbTableName());
        List<SchemaTable> schemaTables = SchemaUtils.buildData(SchemaTable.class, tableList);
        // handle
        if (schemaTables != null && schemaTables.size() > 0) {
            SchemaTable schemaTable = schemaTables.get(0);
            tableMeta.setTitle(schemaTable.getTableComment());
            tableMeta.setTableComment(schemaTable.getTableComment());
            tableMeta.setTableName(schemaTable.getTableName());
            tableMeta.setUpdateAt(sdf.parse(schemaTable.getCreateTime()));
            updateModel(tableMeta);
            devTableColumnService.resetTableColumnByDataBase(tableMeta, false);
            devTableForeignService.resetTableForeignByDataBase(tableMeta, false);
        } else {
            tableMeta.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            isDeleteModel(tableMeta);
            devTableColumnService.resetTableColumnByDataBase(tableMeta, true);
            devTableForeignService.resetTableForeignByDataBase(tableMeta, false);
        }
    }

    /**
     * 表名变更，需要同步数据库的方法
     * <p>
     * 当表名发生变更时，此方法用于同步数据库中的表名。
     *
     * @param form  变更后的表元数据对象，包含变更后的表信息
     * @param model 变更前的表元数据对象，包含变更前的表信息
     * @return 返回变更后的表元数据对象
     */
    public TableMeta handleForm(TableMeta form, TableMeta model) {
        form.setSynced(ColumnSyncedEnum.FALSE.getValue());
        // 表名是否修改
        if (model.getEntityName().equals(form.getEntityName())) {
            return form;
        }
        // 数据库表方可
        if (!TableTypeEnum.TABLE.getValue().equals(model.getTableType())) {
            form.setTableName(null);
            return form;
        }
        Map<String, Object> sqlParams = new HashMap<>();
        // 修正当前表
        // form.setDescription(String.format("update %s from %s[%s]。\n", sdf.format(new Date()), model.getTitle(), model.getEntityName()) + form.getDescription());
        // 备份原来的表
        model.setId(null);
        model.setLinked(LinkedEnum.NO.getValue());
        model.setTableName(null);
        // update 2023-06-25 13:14:15 用户[user]=>组织[org]。
        model.setDescription(String.format("update %s %s[%s]=>%s[%s]。\n", sdf.format(new Date()), model.getTitle(), model.getEntityName(), form.getTitle(), form.getEntityName()) + model.getDescription());
        model.setTableComment(UPDATE_COMMENT_PREFIX + model.getTableComment());
        model.setTitle(UPDATE_COMMENT_PREFIX + model.getTitle());
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        model.setDelStatus(DeleteStatusEnum.IS.getValue());
        model.setDeleteAt(new Date());
        model.setSeqNo(ColumnDefault.SEQ_NO_DELETE);
        // dao.save(model);
        // 数据库表修正
        sqlParams.put("tableId", form.getId());
        sqlParams.put("connectId", form.getConnectId());
        sqlParams.put("entityName", model.getEntityName());// 旧
        sqlParams.put("newEntityName", form.getEntityName());// 新
        sqlParams.put("newComment", form.getTitle());
        // 数据库是否存在表, 切换数据库
        switchDbByConnectId(model.getConnectId());
        List<Map<String, Object>> tableList = dbGenerateDao.dbQueryAllTables(model.getDbType(), model.dbTableName());
        boolean isExistTable = tableList != null && !tableList.isEmpty();
        form.setTableName(isExistTable ? form.getEntityName() : null);
        // 切换数据库，更新库表信息
        if (isExistTable) {
            dynamicDao.execute(model.getDbType() + "_renameTable", sqlParams);
        }
        dao.execute("upgradeMetaAfterRenameTable", sqlParams);

        form.setSynced(ColumnSyncedEnum.TRUE.getValue());
        return form;
    }

    /**
     * 逻辑删除
     * <p>
     * 对指定的表格进行逻辑删除操作，包括修改表名、注释、描述等信息，并更新表的状态。
     *
     * @param model 表格元数据对象，包含表格的基本信息
     */
    public void isDeleteModel(TableMeta model) {
        switchDbByConnectId(model.getConnectId());
        // 格式：table_name_d20230625141315
        String newTableName = String.format("%s_d%s", model.getEntityName(), System.currentTimeMillis());
        String newTitle = DELETE_COMMENT_PREFIX + model.getTitle();
        String newComment = DELETE_COMMENT_PREFIX + (Strings.isNotBlank(model.getTableComment()) ? model.getTableComment() : model.getTitle());
        // delete 2023-06-25 13:14:15 用户[user]=>[user_2023...]。
        String newDescription = String.format("delete %s %s[%s]=>[%s]。\n", sdf.format(new Date()), model.getTitle(), model.getEntityName(), newTableName) + model.getDescription();
        // 数据表处理
        Map<String, Object> sqlParams = new HashMap<>();
        sqlParams.put("tableId", model.getId());
        sqlParams.put("connectId", model.getConnectId());
        sqlParams.put("newEntityName", newTableName);// 新
        sqlParams.put("entityName", model.getEntityName());// 旧
        sqlParams.put("newComment", newComment);
        sqlParams.put("delStatus", DeleteStatusEnum.IS.getValue());
        sqlParams.put("deleteAt", sdf.format(new Date()));
        sqlParams.put("enableStatus", EnableStatusEnum.DISABLED.getValue());
        sqlParams.put("remark", "delete table. \n");
        boolean isExistTable = false;
        if (TableTypeEnum.TABLE.getValue().equals(model.getTableType())) {
            List<Map<String, Object>> tableList = dbGenerateDao.dbQueryAllTables(model.getDbType(), model.dbTableName());
            if (tableList != null && !tableList.isEmpty()) {
                isExistTable = true;
                model.setTableName(newTableName);
            } else {
                model.setTableName(null);
            }
        } else {
            model.setTableName(null);
        }
        // 修正字段、外键、视图
        if (isExistTable) {
            dynamicDao.execute("mysql_renameTable", sqlParams);
        }
        dao.execute("upgradeMetaAfterDelTable", sqlParams);
        // 删除，信息变更
        model.setDescription(newDescription);
        model.setTableComment(newComment);
        model.setTitle(newTitle);
        model.setLinked(LinkedEnum.NO.getValue());
        // 删除，表名变更
        model.setEntityName(newTableName);
        // 删除，标记变更
        model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
        model.setDelStatus(DeleteStatusEnum.IS.getValue());
        model.setDeleteAt(new Date());
        model.setSeqNo(ColumnDefault.SEQ_NO_DELETE);
        dao.save(model);
    }

    /**
     * 复制表格
     * <p>
     * 根据提供的表格ID、标题、实体名称、连接ID、表注释、应用ID和租户代码，复制一个新的表格。
     *
     * @param tableId      源表格ID
     * @param title        新表格标题，如果为空则使用源表格的标题
     * @param entityName   新表格的实体名称
     * @param connectId    新表格的连接ID，如果为空则不设置
     * @param tableComment 新表格的注释，如果为空则不设置
     * @param appId        新表格的应用ID，如果为空则不设置
     * @param tenantCode   新表格的租户代码，如果为空则不设置
     * @return 返回新创建的表格元数据对象
     */
    public TableMeta copyTable(String tableId, String title, String entityName, String connectId, String tableComment, String appId, String tenantCode) {
        // 源模型
        TableMeta form = this.getModel(TableMeta.class, tableId);
        Assert.notNull(form, ApiErrorMsg.IS_NULL);
        title = Strings.isBlank(title) ? form.getTitle() : title;
        // 新模型
        form.setId(null);
        form.setEntityName(entityName);
        form.setTitle(title);
        form.setTableName(null);
        if (Strings.isNotBlank(connectId)) {
            form.setConnectId(connectId);
        }
        if (Strings.isNotBlank(tableComment)) {
            form.setTableComment(tableComment);
        }
        if (Strings.isNotBlank(appId)) {
            form.setAppId(appId);
        }
        if (Strings.isNotBlank(tenantCode)) {
            form.setTenantCode(tenantCode);
        }
        form.setSynced(ColumnSyncedEnum.FALSE.getValue());
        if (!TableSourceTypeEnum.THIRD.getValue().equals(form.getSourceType())) {
            form.setSourceType(TableSourceTypeEnum.CREATION.getValue());
        }
        form.setPackBusData(0);
        TableMeta formMap = this.createModel(form);
        form.setId(formMap.getId());
        // 源模型字段
        Map<String, Object> params = new HashMap<>();
        params.put("tableId", tableId);
        List<ColumnMeta> columnMetas = queryModel(ColumnMeta.class, params);
        if (columnMetas == null || columnMetas.size() < 1) {
            return form;
        }
        // 新模型字段
        for (ColumnMeta meta : columnMetas) {
            meta.setId(null);
            meta.setTableId(form.getId());
            meta.setTableName(form.getEntityName());
            meta.setSynced(ColumnSyncedEnum.FALSE.getValue());
            devTableColumnService.createModel(meta);
        }

        return form;
    }

}
