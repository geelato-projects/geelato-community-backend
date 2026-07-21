package cn.geelato.web.platform.srv.security;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.UserDelegateMap;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.service.UserDelegateMapService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * 用户委托关系接口
 * <p>
 * 提供委托/助理关系的增删改查，供个人中心维护使用。
 * 其他业务模块如需消费委托关系，请直接查询 platform_user_r_delegate 表。
 *
 * @author geelato
 */
@ApiRestController(value = "/security/user/delegate")
@Slf4j
public class UserDelegateMapController extends BaseController {
    private static final Class<UserDelegateMap> CLAZZ = UserDelegateMap.class;
    private final UserDelegateMapService userDelegateMapService;

    @Autowired
    public UserDelegateMapController(UserDelegateMapService userDelegateMapService) {
        this.userDelegateMapService = userDelegateMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return userDelegateMapService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
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
            return ApiResult.success(userDelegateMapService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            UserDelegateMap model = userDelegateMapService.getModel(CLAZZ, id);
            return ApiResult.success(model);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public ApiResult insert(@RequestBody UserDelegateMap form) {
        try {
            return ApiResult.success(userDelegateMapService.createModels(form));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody UserDelegateMap form) {
        try {
            return ApiResult.success(userDelegateMapService.createModels(form));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            UserDelegateMap model = userDelegateMapService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            userDelegateMapService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 唯一性校验
     * <p>
     * type=duplicate：校验同委托人+代理人+scope 是否重复，返回 true 表示可用。
     *
     * @param type 校验类型
     * @param form 表单数据
     */
    @RequestMapping(value = "/validate/{type}", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@PathVariable(required = true) String type, @RequestBody UserDelegateMap form) {
        try {
            if ("duplicate".equalsIgnoreCase(type)) {
                return ApiResult.success(userDelegateMapService.validateDuplicate(
                        form.getId(), form.getUserId(), form.getDelegateUserId(), form.getScope()));
            }
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
