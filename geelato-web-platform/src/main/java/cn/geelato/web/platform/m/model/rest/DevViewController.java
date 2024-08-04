package cn.geelato.web.platform.m.model.rest;

import cn.geelato.web.platform.m.model.service.DevViewService;
import cn.geelato.web.platform.m.security.entity.DataItems;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.view.TableView;
import cn.geelato.web.platform.m.base.rest.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author diabl
 * @description: 视图操作类
 * @date 2023/6/15 9:37
 */
@Controller
@RequestMapping(value = "/api/model/view")
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
    @ResponseBody
    public ApiPagedResult<DataItems> pageQuery(HttpServletRequest req) {
        ApiPagedResult<DataItems> result = new ApiPagedResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = devViewService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult<>();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(devViewService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult<TableView> get(@PathVariable(required = true) String id) {
        ApiResult<TableView> result = new ApiResult<>();
        try {
            TableView model = devViewService.getModel(CLAZZ, id);
            devViewService.viewColumnMeta(model);
            result.setData(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody TableView form) {
        ApiResult result = new ApiResult<>();
        try {
            form.afterSet();
            // 视图
            devViewService.viewColumnMapperDBObject(form);
            // ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                result.setData(devViewService.updateModel(form));
            } else {
                result.setData(devViewService.createModel(form));
            }
            // 刷新实体缓存
            if (Strings.isNotEmpty(form.getViewName())) {
                metaManager.refreshDBMeta(form.getViewName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    public ApiResult isDelete(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            TableView model = devViewService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
            devViewService.isDeleteModel(model);
            // 刷新实体缓存
            if (Strings.isNotEmpty(model.getViewName())) {
                metaManager.removeLiteMeta(model.getViewName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult validate(@RequestBody TableView form) {
        ApiResult result = new ApiResult();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("view_name", form.getViewName().toLowerCase(Locale.ENGLISH));
            params.put("connect_id", form.getConnectId());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("app_id", form.getAppId());
            params.put("tenant_code", form.getTenantCode());
            result.setData(devViewService.validate("platform_dev_view", form.getId(), params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.VALIDATE_FAIL);
        }

        return result;
    }
}
