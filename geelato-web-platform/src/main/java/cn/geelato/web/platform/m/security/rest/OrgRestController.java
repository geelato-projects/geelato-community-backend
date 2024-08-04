package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.Org;
import cn.geelato.web.platform.m.security.service.OrgService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
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
 */
@Controller
@RequestMapping(value = "/api/security/org")
public class OrgRestController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<Org> CLAZZ = Org.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("name", "code", "description"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(OrgRestController.class);
    @Autowired
    private OrgService orgService;

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = orgService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult query(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            result.setData(orgService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryByParams", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult query(@RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            if (params != null && !params.isEmpty()) {
                FilterGroup filterGroup = new FilterGroup();
                filterGroup.addFilter("id", FilterGroup.Operator.in, String.valueOf(params.get("ids")));
                return result.setData(orgService.queryModel(CLAZZ, filterGroup));
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/queryTree", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult queryTree(HttpServletRequest req) {
        ApiResult result = new ApiResult();
        try {
            Map<String, Object> params = this.getQueryParameters(CLAZZ, req);
            return result.setData(orgService.queryTree(params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult get(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            result.setData(orgService.getModel(CLAZZ, id));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody Org form) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(form.getId())) {
                result.setData(orgService.updateModel(form));
            } else {
                result.setData(orgService.createModel(form));
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
            Org model = orgService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            orgService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult validate(@RequestBody Org form) {
        ApiResult result = new ApiResult();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("code", form.getCode());
            params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
            params.put("tenant_code", form.getTenantCode());
            result.setData(orgService.validate("platform_org", form.getId(), params));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.VALIDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/getCompany/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiResult getCompany(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            result.setData(orgService.getCompany(id));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }
}
