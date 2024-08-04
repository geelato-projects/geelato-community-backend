package cn.geelato.web.platform.m.base.rest;


import cn.geelato.web.platform.m.base.service.ViewService;
import cn.geelato.web.platform.m.model.service.DevTableService;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.Ctx;
import cn.geelato.lang.api.ApiMetaResult;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.enums.TableSourceTypeEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.core.orm.DbGenerateDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.*;

/**
 * @author itechgee@126.com
 * @date 2017/6/3.
 */
@Controller
@RequestMapping(value = "/api/meta/ddl/")
public class MetaDdlController extends BaseController {

    @Autowired
    protected DbGenerateDao dbGenerateDao;
    @Autowired
    private DevTableService devTableService;
    @Autowired
    private ViewService viewService;

    private static final Logger logger = LoggerFactory.getLogger(MetaDdlController.class);


    /**
     * 新建或更新表，不删除表字段
     *
     * @param entity 实体名称
     */
    @RequestMapping(value = {"table/{entity}"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult recreate(@PathVariable("entity") String entity) {
        ApiMetaResult result = new ApiMetaResult();
        try {
            dbGenerateDao.createOrUpdateOneTable(entity, false);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error().setMsg(ex.getCause().getMessage());
        }
        return result;
    }

    @RequestMapping(value = {"tables/{appId}"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult recreates(@PathVariable("appId") String appId) {
        ApiMetaResult result = new ApiMetaResult();
        Map<String, Object> tableResult = new LinkedHashMap<>();
        String tenantCode = Ctx.getCurrentTenantCode();
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
            result.setData(tableResult);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error().setMsg(String.format("%s, %s", errorModel, ex.getCause().getMessage())).setData(tableResult);
        } finally {
            // 刷新缓存
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                Map<String, String> table = new HashMap<>();
                table.put("app_id", appId);
                table.put("tenant_code", tenantCode);
                MetaManager.singleInstance().parseDBMeta(dao, table);
            }
        }
        return result;
    }

    @RequestMapping(value = {"views/{appId}"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult reViewCreates(@PathVariable("appId") String appId) {
        ApiMetaResult result = new ApiMetaResult();
        Map<String, Object> tableResult = new LinkedHashMap<>();
        String tenantCode = Ctx.getCurrentTenantCode();
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
            result.setData(tableResult);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error().setMsg(String.format("%s, %s", errorModel, ex.getMessage())).setData(tableResult);
        } finally {
            // 刷新缓存
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                Map<String, String> table = new HashMap<>();
                table.put("app_id", appId);
                table.put("tenant_code", tenantCode);
                MetaManager.singleInstance().parseDBMeta(dao, table);
            }
        }

        return result;
    }

    @RequestMapping(value = {"viewOne/{id}"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult reViewCreate(@PathVariable("id") String id) {
        ApiMetaResult result = new ApiMetaResult();
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
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error().setMsg(ex.getMessage());
        } finally {
            if (Strings.isNotBlank(entityName)) {
                MetaManager.singleInstance().refreshDBMeta(entityName);
            }
        }

        return result;
    }

    /**
     * 新建更新视图
     */
    @RequestMapping(value = {"view/{view}"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult recreate(@PathVariable("view") String view, @RequestBody Map<String, String> params) {
        ApiMetaResult result = new ApiMetaResult();
        dbGenerateDao.createOrUpdateView(view, params.get("sql"));
        return result;
    }

    @RequestMapping(value = {"view/valid/{connectId}"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult<Boolean> validateView(@PathVariable("connectId") String connectId, @RequestBody Map<String, String> params) {
        ApiMetaResult<Boolean> result = new ApiMetaResult();
        try {
            boolean isValid = dbGenerateDao.validateViewSql(connectId, params.get("sql"));
            result.success().setData(isValid);
        } catch (SQLException e) {
            logger.error(e.getMessage());
            result.success().setData(false);
        }
        return result;
    }

    @RequestMapping(value = {"redis/refresh"}, method = {RequestMethod.POST}, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiMetaResult refreshRedis(@RequestBody Map<String, String> params) {
        ApiMetaResult result = new ApiMetaResult();
        try {
            Map<String, String> table = new HashMap<>();
            table.put("id", params.get("tableId"));
            table.put("entity_name", params.get("entityName"));
            table.put("connect_id", params.get("connectId"));
            table.put("app_id", params.get("appId"));
            table.put("tenant_code", Strings.isNotBlank(params.get("tenantCode")) ? params.get("tenantCode") : Ctx.getCurrentTenantCode());
            MetaManager.singleInstance().parseDBMeta(dao, table);
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            result.error().setMsg(ex.getCause().getMessage());
        }
        return result;
    }


}
