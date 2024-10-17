package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.Ctx;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.UserStockMap;
import cn.geelato.web.platform.m.security.service.UserStockMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;

@ApiRestController(value = "/security/user/stock")
@Slf4j
public class UserStockMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<UserStockMap> CLAZZ = UserStockMap.class;

    private UserStockMapService userStockMapService;

    @Autowired
    public void setUserStockMapService(UserStockMapService userStockMapService) {
        this.userStockMapService = userStockMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    public ApiPagedResult pageQuery() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, OPERATORMAP);
            ApiPagedResult result = userStockMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            return result;
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
            List<UserStockMap> list = userStockMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            return ApiResult.success(list);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/total", method = RequestMethod.GET)
    public ApiResult total() {
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters();
            Map<String, Object> params = this.getQueryParameters(CLAZZ);
            params.put("userId", Ctx.getCurrentUser().getUserId());
            List<UserStockMap> list = userStockMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            return ApiResult.success(list == null ? 0 : list.size());
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public ApiResult create(@RequestBody UserStockMap form) {
        try {
            String userId = Ctx.getCurrentUser().getUserId();
            // 删除已存在的
            isDelete(Ctx.getCurrentUser().getUserId(), form.getStockId());
            // 创建
            List<String> stockIds = new ArrayList<>();
            if (StringUtils.isNotBlank(form.getStockId())) {
                String[] stockIdArr = form.getStockId().split(",");
                stockIds.addAll(Arrays.asList(stockIdArr));
            }
            List<UserStockMap> uMap = new ArrayList<>();
            for (String stockId : stockIds) {
                form.setStockId(stockId);
                form.setUserId(userId);
                UserStockMap userStockMap = userStockMapService.createModel(form);
                uMap.add(userStockMap);
            }
            return ApiResult.success(uMap);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/remove/{stockId}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> remove(@PathVariable(required = true) String stockId) {
        try {
            isDelete(Ctx.getCurrentUser().getUserId(), stockId);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    private void isDelete(String userId, String stockId) {
        FilterGroup filterGroup = new FilterGroup();
        filterGroup.addFilter("userId", userId);
        filterGroup.addFilter("stockId", FilterGroup.Operator.in, stockId);
        List<UserStockMap> list = userStockMapService.queryModel(CLAZZ, filterGroup);
        if (list != null && list.size() > 0) {
            for (UserStockMap u : list) {
                userStockMapService.isDeleteModel(u);
            }
        }
    }
}
