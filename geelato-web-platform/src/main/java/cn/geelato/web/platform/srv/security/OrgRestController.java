package cn.geelato.web.platform.srv.security;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.meta.Org;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.service.OrgService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController(value = "/security/org")
@Slf4j
public class OrgRestController extends BaseController {
    private static final Class<Org> CLAZZ = Org.class;
    private final OrgService orgService;

    @Autowired
    public OrgRestController(OrgService orgService) {
        this.orgService = orgService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return orgService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public ApiResult query() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(orgService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryByParams", method = RequestMethod.POST)
    public ApiResult query(@RequestBody Map<String, Object> params) {
        try {
            if (params == null || params.isEmpty()) {
                throw new RuntimeException("params is null");
            }
            FilterGroup filterGroup = new FilterGroup();
            filterGroup.addFilter("id", FilterGroup.Operator.in, String.valueOf(params.get("ids")));
            return ApiResult.success(orgService.queryModel(CLAZZ, filterGroup));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryTree", method = RequestMethod.GET)
    public ApiResult queryTree() {
        try {
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            return ApiResult.success(orgService.queryTree(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(orgService.getModel(CLAZZ, id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody Org form) {
        try {
            if (Strings.isNotBlank(form.getId())) {
                return ApiResult.success(orgService.updateModel(form));
            } else {
                return ApiResult.success(orgService.createModel(form));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            Org model = orgService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            orgService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@RequestBody Org form) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(orgService.validate("platform_org", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/getCompany/{id}", method = RequestMethod.GET)
    public ApiResult getCompany(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(orgService.getCompany(id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/getDepartment/{id}", method = RequestMethod.GET)
    public ApiResult getDepartment(@PathVariable(required = true) String id) {
        try {
            return ApiResult.success(orgService.getDepartment(id));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
