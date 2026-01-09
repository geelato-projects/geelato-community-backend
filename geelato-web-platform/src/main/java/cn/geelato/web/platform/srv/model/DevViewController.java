package cn.geelato.web.platform.srv.model;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.model.service.DevViewService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author diabl
 * 视图操作类
 */
@ApiRestController("/model/view")
@Slf4j
public class DevViewController extends BaseController {
    private static final Class<TableView> CLAZZ = TableView.class;
    private final MetaManager metaManager = MetaManager.singleInstance();
    private final DevViewService devViewService;

    @Autowired
    public DevViewController(DevViewService devViewService) {
        this.devViewService = devViewService;
    }


    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<?> pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return devViewService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(devViewService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            TableView model = devViewService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getValue());
            devViewService.isDeleteModel(model);
            // 刷新实体缓存
            if (Strings.isNotEmpty(model.getViewName())) {
                metaManager.removeLiteMeta(model.getViewName());
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody TableView form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("view_name", form.getViewName().toLowerCase(Locale.ENGLISH));
            params.put("connect_id", form.getConnectId());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(devViewService.validate("platform_dev_view", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }
}
