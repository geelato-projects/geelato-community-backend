package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.*;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.util.ClassUtils;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author diabl
 * @description: 表单视图服务类
 */
@Component
public class DevViewService extends BaseSortableService {
    private static final String DELETE_COMMENT_PREFIX = "已删除；";
    private static final String UPDATE_COMMENT_PREFIX = "已变更；";
    private static final Logger logger = LoggerFactory.getLogger(DevViewService.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATETIME);

    public List<TableView> getTableView(String connectId, String entityName) {
        Map<String, Object> params = new HashMap<>();
        params.put("connectId", connectId);
        params.put("entityName", entityName);
        params.put("viewType", ViewTypeEnum.DEFAULT.getCode());
        params.put("enableStatus", ColumnDefault.ENABLE_STATUS_VALUE);
        List<TableView> tableViewList = queryModel(TableView.class, params);
        return tableViewList;
    }

    /**
     * 仅创建或更新默认视图
     *
     * 根据传入的表格元数据对象（tableMeta）和视图参数（viewParams），创建或更新指定表格的默认视图。
     *
     * @param tableMeta   表格元数据对象，包含表格的基本信息
     * @param viewParams  视图参数，包含视图的列和构造SQL
     */
    public void createOrUpdateDefaultTableView(TableMeta tableMeta, Map<String, Object> viewParams) {
        Assert.notNull(tableMeta, ApiErrorMsg.IS_NULL);
        String viewColumns = (String) viewParams.get("viewColumns");
        String viewConstruct = (String) viewParams.get("viewConstruct");
        if (Strings.isBlank(viewColumns) || Strings.isBlank(viewConstruct)) {
            return;
        }
        List<TableView> tableViewList = getTableView(tableMeta.getConnectId(), tableMeta.getEntityName());
        if (tableViewList != null && !tableViewList.isEmpty()) {
            TableView meta = tableViewList.get(0);
            if (tableViewList.size() > 1) {
                for (int i = 1; i < tableViewList.size(); i++) {
                    isDeleteModel(tableViewList.get(i));
                }
            }
            meta.setAppId(tableMeta.getAppId());
            meta.setViewConstruct(viewConstruct);
            meta.setViewColumn(viewColumns);
            meta.setTitle(String.format("%s的默认视图", tableMeta.getTitle()));
            meta.setViewName(String.format("v_%s", tableMeta.getEntityName()));
            meta.afterSet();
            updateModel(meta);
        } else {
            TableView meta = new TableView();
            meta.setAppId(tableMeta.getAppId());
            meta.setTenantCode(tableMeta.getTenantCode());
            meta.setConnectId(tableMeta.getConnectId());
            meta.setEntityName(tableMeta.getEntityName());
            meta.setTitle(String.format("%s的默认视图", tableMeta.getTitle()));
            meta.setViewName(String.format("v_%s", tableMeta.getEntityName()));
            meta.setViewType(ViewTypeEnum.DEFAULT.getCode());
            meta.setViewConstruct(viewConstruct);
            meta.setViewColumn(viewColumns);
            meta.setLinked(tableMeta.getLinked());
            meta.setSeqNo(ColumnDefault.SEQ_NO_FIRST);
            meta.afterSet();
            createModel(meta);
        }
    }

    public void viewColumnMapperDBObject(TableView form) {
        if (Strings.isNotBlank(form.getViewColumn())) {
            List<Object> list = new ArrayList<>();
            List<String> columnNames = new ArrayList<>();
            JSONArray columnData = JSONArray.parse(form.getViewColumn());
            columnData.forEach(x -> {
                ColumnMeta meta = JSON.parseObject(x.toString(), ColumnMeta.class);
                meta.afterSet();
                columnNames.add(meta.getName());
                list.add(ClassUtils.toMapperDBObject(meta));
            });
            // 默认字段
            List<ColumnMeta> metaList = MetaManager.singleInstance().getDefaultColumn();
            if (metaList != null && metaList.size() > 0) {
                for (ColumnMeta meta : metaList) {
                    if (!columnNames.contains(meta.getName())) {
                        meta.setAppId(form.getAppId());
                        meta.setTenantCode(form.getTenantCode());
                        meta.setSynced(ColumnSyncedEnum.FALSE.getValue());
                        meta.setEncrypted(ColumnEncryptedEnum.FALSE.getValue());
                        meta.setTableName(form.getViewName());
                        list.add(ClassUtils.toMapperDBObject(meta));
                    }
                }
            }
            form.setViewColumn(JSON.toJSONString(list));
        }
    }

    public void viewColumnMeta(TableView form) {
        if (Strings.isNotBlank(form.getViewColumn())) {
            List<Object> list = new ArrayList<>();
            JSONArray columnData = JSONArray.parse(form.getViewColumn());
            columnData.forEach(x -> {
                Map<String, Object> m = JSON.parseObject(x.toString(), Map.class);
                list.add(ClassUtils.toMeta(ColumnMeta.class, m));
            });
            form.setViewColumn(JSON.toJSONString(list));
        }
    }

    public void isDeleteModel(TableView model) {
        String newViewName = String.format("%s_d%s", model.getViewName(), System.currentTimeMillis());
        String newTitle = DELETE_COMMENT_PREFIX + model.getTitle();
        String newDescription = String.format("delete %s %s[%s]=>[%s]。\n", sdf.format(new Date()), model.getTitle(), model.getViewName(), newViewName) + model.getDescription();
        Map<String, Object> sqlParams = new HashMap<>();
        sqlParams.put("viewId", model.getId());
        sqlParams.put("viewName", model.getViewName());
        sqlParams.put("newViewName", newViewName);// 新
        sqlParams.put("newDescription", newDescription);
        sqlParams.put("delStatus", DeleteStatusEnum.IS.getCode());
        sqlParams.put("deleteAt", sdf.format(new Date()));
        sqlParams.put("enableStatus", EnableStatusEnum.DISABLED.getCode());
        sqlParams.put("remark", "delete table. \n");
        // 数据库视图
        try {
            Map<String, Object> viewMap = dao.getJdbcTemplate().queryForMap(String.format(MetaDaoSql.SQL_SHOW_CREATE_VIEW, model.getViewName()));
            if (viewMap != null && !viewMap.isEmpty()) {
                String createView = viewMap.get("Create View") != null ? String.valueOf(viewMap.get("Create View")) : "";
                sqlParams.put("isView", true);
                // SQL SECURITY DEFINER VIEW `` AS
                sqlParams.put("newSql", createView.replace(String.format("SQL SECURITY DEFINER VIEW `%s` AS", model.getViewName()),
                        String.format("SQL SECURITY DEFINER VIEW `%s` AS", newViewName)));
            }
        } catch (Exception ex) {
            logger.info(String.format("%s 视图不存在。", model.getViewName()));
        }
        // 修正字段、外键、视图
        dao.execute("metaResetOrDeleteView", sqlParams);
        // 删除，信息变更
        model.setViewName(newViewName);
        model.setDescription(newDescription);
        model.setTitle(newTitle);
        // 删除，标记变更
        model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        model.setDelStatus(DeleteStatusEnum.IS.getCode());
        model.setDeleteAt(new Date());
        model.setSeqNo(ColumnDefault.SEQ_NO_DELETE);
        dao.save(model);
    }

    private List<Map<String, Object>> queryInformationSchemaViews(String viewName) {
        List<Map<String, Object>> tableList = new ArrayList<>();
        if (Strings.isNotBlank(viewName)) {
            String tableSql = String.format(MetaDaoSql.INFORMATION_SCHEMA_VIEWS, MetaDaoSql.TABLE_SCHEMA_METHOD, " AND TABLE_NAME='" + viewName + "'");
            logger.info(tableSql);
            tableList = dao.getJdbcTemplate().queryForList(tableSql);
        }
        return tableList;
    }

}
