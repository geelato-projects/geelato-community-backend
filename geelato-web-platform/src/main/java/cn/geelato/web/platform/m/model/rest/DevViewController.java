package cn.geelato.web.platform.m.model.rest;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.model.service.DevViewService;
import cn.geelato.web.platform.m.security.entity.DataItems;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;

/**
 * @author diabl
 * @description: 视图操作类
 */
@ApiRestController("/model/view")
@Slf4j
public class DevViewController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<TableView> CLAZZ = TableView.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("title", "viewName", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final MetaManager metaManager = MetaManager.singleInstance();
    private final Logger logger = LoggerFactory.getLogger(DevViewController.class);
    @Autowired
    private DevViewService devViewService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult<DataItems> pageQuery(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            return devViewService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query(HttpServletRequest req) {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            return ApiResult.success(devViewService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<TableView> get(@PathVariable(required = true) String id) {
        try {
            TableView model = devViewService.getModel(CLAZZ, id);
            devViewService.viewColumnMeta(model);
            return ApiResult.success(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody TableView form) {
        try {
            form.afterSet();
            // 视图
            devViewService.viewColumnMapperDBObject(form);
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                form = devViewService.updateModel(form);
            } else {
                form = devViewService.createModel(form);
            }
            // 刷新实体缓存
            if (Strings.isNotEmpty(form.getViewName())) {
                metaManager.refreshDBMeta(form.getViewName());
            }
            return ApiResult.success(form);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            TableView model = devViewService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            devViewService.isDeleteModel(model);
            // 刷新实体缓存
            if (Strings.isNotEmpty(model.getViewName())) {
                metaManager.removeLiteMeta(model.getViewName());
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody TableView form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("view_name", form.getViewName().toLowerCase(Locale.ENGLISH));
            params.put("connect_id", form.getConnectId());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(devViewService.validate("platform_dev_view", form.getId(), params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
