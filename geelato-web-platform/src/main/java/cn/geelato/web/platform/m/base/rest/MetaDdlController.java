package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableSourceTypeEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.orm.DbGenerateDao;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.ViewService;
import cn.geelato.web.platform.m.model.service.DevTableService;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.sql.SQLException;
import java.util.*;

/**
 * @author itechgee@126.com
 */
@ApiRestController(value = "/meta/ddl")
public class MetaDdlController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(MetaDdlController.class);
    @Autowired
    protected DbGenerateDao dbGenerateDao;
    @Autowired
    private DevTableService devTableService;
    @Autowired
    private ViewService viewService;

    /**
     * 新建或更新表，不删除表字段
     *
     * @param entity 实体名称
     */
    @RequestMapping(value = {"/table/{entity}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult recreate(@PathVariable("entity") String entity) {
        try {
            dbGenerateDao.createOrUpdateOneTable(entity, false);
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return ApiMetaResult.fail(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
        }
    }

    @RequestMapping(value = {"/tables/{appId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult recreates(@PathVariable("appId") String appId) {
        Map<String, Object> tableResult = new LinkedHashMap<>();
        String tenantCode = SessionCtx.getCurrentTenantCode();
        String errorModel = "";
        try {
            if (Strings.isNotBlank(appId)) {
                FilterGroup filterGroup = new FilterGroup();
                filterGroup.addFilter("appId", appId);
                filterGroup.addFilter("tenantCode", tenantCode);
                filterGroup.addFilter("enableStatus", String.valueOf(EnableStatusEnum.ENABLED.getCode()));
                List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, filterGroup);
                if (tableMetas != null) {
                    tableMetas.sort(new Comparator<TableMeta>() {
                        @Override
                        public int compare(TableMeta o1, TableMeta o2) {
                            return o1.getEntityName().compareToIgnoreCase(o2.getEntityName());
                        }
                    });
                    for (TableMeta meta : tableMetas) {
                        tableResult.put(meta.getEntityName(), false);
                    }
                    for (int i = 0; i < tableMetas.size(); i++) {
                        if (TableSourceTypeEnum.CREATION.getValue().equalsIgnoreCase(tableMetas.get(i).getSourceType())) {
                            errorModel = String.format("Error Model: %s（%s）", tableMetas.get(i).getTitle(), tableMetas.get(i).getEntityName());
                            dbGenerateDao.createOrUpdateOneTable(tableMetas.get(i).getEntityName(), false);
                            logger.info(String.format("成功插入第 %s 个。表名：%s", (i + 1), tableMetas.get(i).getEntityName()));
                            tableResult.put(tableMetas.get(i).getEntityName(), true);
                        } else {
                            tableResult.put(tableMetas.get(i).getEntityName(), "ignore");
                        }
                    }
                }
            }
            return ApiMetaResult.success(tableResult);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            return ApiMetaResult.fail(tableResult, String.format("%s, %s", errorModel, message));
        } finally {
            // 刷新缓存
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                Map<String, String> table = new HashMap<>();
                table.put("app_id", appId);
                table.put("tenant_code", tenantCode);
                MetaManager.singleInstance().parseDBMeta(dao, table);
            }
        }
    }

    @RequestMapping(value = {"/views/{appId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult reViewCreates(@PathVariable("appId") String appId) {
        Map<String, Object> tableResult = new LinkedHashMap<>();
        String tenantCode = SessionCtx.getCurrentTenantCode();
        String errorModel = "";
        try {
            if (Strings.isNotBlank(appId)) {
                FilterGroup filterGroup = new FilterGroup();
                filterGroup.addFilter("tenantCode", tenantCode);
                List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, filterGroup);
                filterGroup.addFilter("enableStatus", String.valueOf(EnableStatusEnum.ENABLED.getCode()));
                filterGroup.addFilter("appId", appId);
                List<TableView> viewMetas = viewService.queryModel(TableView.class, filterGroup);
                if (viewMetas != null) {
                    viewMetas.sort(new Comparator<TableView>() {
                        @Override
                        public int compare(TableView o1, TableView o2) {
                            return o1.getViewName().compareToIgnoreCase(o2.getViewName());
                        }
                    });
                    for (TableView meta : viewMetas) {
                        tableResult.put(meta.getViewName(), false);
                    }
                    for (int i = 0; i < viewMetas.size(); i++) {
                        TableView viewMeta = viewMetas.get(i);
                        Optional<TableMeta> tableMetaResult = tableMetas.stream().filter(t -> t.getEntityName().equalsIgnoreCase(viewMeta.getEntityName())).findFirst();
                        if (tableMetaResult.isEmpty()) {
                            logger.warn(String.format("%s（%s），不存在可以关联的数据库表。", viewMeta.getTitle(), viewMeta.getViewName()));
                            tableResult.put(viewMeta.getViewName(), "不存在可以关联的数据库表");
                            continue;
                        }
                        if (!tableMetaResult.get().getSynced()) {
                            logger.warn(String.format("%s（%s），模型与数据库表不一致。", viewMeta.getTitle(), viewMeta.getViewName()));
                            tableResult.put(viewMeta.getViewName(), "模型与数据库表不一致");
                            continue;
                        }
                        if (Strings.isBlank(viewMeta.getViewConstruct())) {
                            logger.warn(String.format("%s（%s），视图语句不存在。", viewMeta.getTitle(), viewMeta.getViewName()));
                            tableResult.put(viewMeta.getViewName(), "视图语句不存在");
                            continue;
                        }
                        boolean isValid = false;
                        try {
                            logger.info(viewMeta.getViewName() + " - " + viewMeta.getViewConstruct());
                            isValid = dbGenerateDao.validateViewSql(viewMeta.getConnectId(), viewMeta.getViewConstruct());
                        } catch (Exception ex) {
                            isValid = false;
                        }
                        if (!isValid) {
                            logger.warn(String.format("%s（%s），视图语句验证失败。", viewMeta.getTitle(), viewMeta.getViewName()));
                            tableResult.put(viewMeta.getViewName(), "视图语句验证失败");
                            continue;
                        }
                        errorModel = String.format("Error View: %s（%s）", viewMeta.getTitle(), viewMeta.getViewName());
                        dbGenerateDao.createOrUpdateView(viewMeta.getViewName(), viewMeta.getViewConstruct());
                        logger.info(String.format("成功插入第 %s 个。视图名：%s", (i + 1), viewMeta.getViewName()));
                        tableResult.put(viewMeta.getViewName(), true);
                    }
                }
            }
            return ApiMetaResult.success(tableResult);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            return ApiMetaResult.fail(tableResult, String.format("%s, %s", errorModel, message));
        } finally {
            // 刷新缓存
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                Map<String, String> table = new HashMap<>();
                table.put("app_id", appId);
                table.put("tenant_code", tenantCode);
                MetaManager.singleInstance().parseDBMeta(dao, table);
            }
        }
    }

    @RequestMapping(value = {"/viewOne/{id}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult reViewCreate(@PathVariable("id") String id) {
        String entityName = null;
        try {
            if (Strings.isNotBlank(id)) {
                TableView viewMeta = viewService.getModel(TableView.class, id);
                Assert.notNull(viewMeta, "视图信息查询失败");
                Map<String, Object> tableParams = new HashMap<>();
                tableParams.put("entityName", viewMeta.getEntityName());
                List<TableMeta> tableMetas = devTableService.queryModel(TableMeta.class, tableParams);
                if (tableMetas.size() == 0) {
                    logger.warn(String.format("%s（%s），不存在可以关联的数据库表。", viewMeta.getTitle(), viewMeta.getViewName()));
                    throw new RuntimeException("不存在可以关联的数据库表");
                }
                if (!tableMetas.get(0).getSynced()) {
                    logger.warn(String.format("%s（%s），模型与数据库表不一致。", viewMeta.getTitle(), viewMeta.getViewName()));
                    throw new RuntimeException("模型与数据库表不一致，需同步");
                }
                if (Strings.isBlank(viewMeta.getViewConstruct())) {
                    logger.warn(String.format("%s（%s），视图语句不存在。", viewMeta.getTitle(), viewMeta.getViewName()));
                    throw new RuntimeException("视图语句不存在");
                }
                boolean isValid = false;
                try {
                    logger.info(viewMeta.getViewName() + " - " + viewMeta.getViewConstruct());
                    isValid = dbGenerateDao.validateViewSql(viewMeta.getConnectId(), viewMeta.getViewConstruct());
                } catch (Exception ex) {
                    isValid = false;
                }
                if (!isValid) {
                    logger.warn(String.format("%s（%s），视图语句验证失败。", viewMeta.getTitle(), viewMeta.getViewName()));
                    throw new RuntimeException("视图语句验证失败");
                }
                entityName = viewMeta.getEntityName();
                dbGenerateDao.createOrUpdateView(viewMeta.getViewName(), viewMeta.getViewConstruct());
            }
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return ApiMetaResult.fail(ex.getMessage());
        } finally {
            if (Strings.isNotBlank(entityName)) {
                MetaManager.singleInstance().refreshDBMeta(entityName);
            }
        }
    }

    /**
     * 新建更新视图
     */
    @RequestMapping(value = {"/view/{view}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult recreate(@PathVariable("view") String view, @RequestBody Map<String, String> params) {
        dbGenerateDao.createOrUpdateView(view, params.get("sql"));
        return ApiMetaResult.successNoResult();
    }

    @RequestMapping(value = {"/view/valid/{connectId}"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult<Boolean> validateView(@PathVariable("connectId") String connectId, @RequestBody Map<String, String> params) {
        try {
            boolean isValid = dbGenerateDao.validateViewSql(connectId, params.get("sql"));
            return ApiMetaResult.success(isValid);
        } catch (SQLException e) {
            logger.error(e.getMessage());
            return ApiMetaResult.success(false);
        }
    }

    @RequestMapping(value = {"/redis/refresh"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiMetaResult refreshRedis(@RequestBody Map<String, String> params) {
        try {
            Map<String, String> table = new HashMap<>();
            table.put("id", params.get("tableId"));
            table.put("entity_name", params.get("entityName"));
            table.put("connect_id", params.get("connectId"));
            table.put("app_id", params.get("appId"));
            table.put("tenant_code", Strings.isNotBlank(params.get("tenantCode")) ? params.get("tenantCode") : SessionCtx.getCurrentTenantCode());
            MetaManager.singleInstance().parseDBMeta(dao, table);
            return ApiMetaResult.successNoResult();
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            return ApiMetaResult.fail(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
        }
    }
}
