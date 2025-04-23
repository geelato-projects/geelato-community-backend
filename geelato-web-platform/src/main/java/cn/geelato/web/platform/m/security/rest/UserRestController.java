package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.StringUtils;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.common.security.Org;
import cn.geelato.web.common.security.User;
import cn.geelato.web.platform.m.security.entity.UserStockMap;
import cn.geelato.web.platform.m.security.service.OrgService;
import cn.geelato.web.platform.m.security.service.UserService;
import cn.geelato.web.platform.m.security.service.UserStockMapService;
import cn.geelato.web.platform.utils.EncryptUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@ApiRestController(value = "/security/user")
@Slf4j
public class UserRestController extends BaseController {
    private static final int DEFAULT_PASSWORD_DIGIT = 8;
    private static final Class<User> CLAZZ = User.class;
    private final UserService userService;
    private final OrgService orgService;
    private final UserStockMapService userStockMapService;

    @Autowired
    public UserRestController(UserService userService, OrgService orgService, UserStockMapService userStockMapService) {
        this.userService = userService;
        this.orgService = orgService;
        this.userStockMapService = userStockMapService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            ApiPagedResult result = userService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            DataItems<List<User>> dataItems = (DataItems<List<User>>) result.getData();
            // 清理不需要展示的数据
            userFormat(dataItems.getItems());
            // 是否是当前用户的常用联系人
            userStockFormat(dataItems.getItems(), SessionCtx.getCurrentUser().getUserId());
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/pageQueryStock", method = RequestMethod.POST)
    public ApiPagedResult pageQueryStock(boolean stocked, boolean stockSearch) {
        try {
            // 搜索条件
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            // 获取当前用户常用联系人
            if (stockSearch) {
                Map<String, Object> params = new HashMap<>();
                params.put("userId", SessionCtx.getCurrentUser().getUserId());
                List<UserStockMap> userStockMaps = userStockMapService.queryModel(UserStockMap.class, params);
                List<String> stockIds = new ArrayList<>();
                if (userStockMaps != null && userStockMaps.size() > 0) {
                    stockIds = userStockMaps.stream().map(UserStockMap::getStockId).collect(Collectors.toList());
                    filterGroup.addFilter("id", FilterGroup.Operator.in, StringUtils.join(stockIds, ","));
                } else {
                    filterGroup.addFilter("id", FilterGroup.Operator.in, "null");
                }
            }
            // 分页查询
            ApiPagedResult result = userService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            DataItems<List<User>> dataItems = (DataItems<List<User>>) result.getData();
            // 清理不需要展示的数据
            userFormat(dataItems.getItems());
            // 是否是当前用户的常用联系人
            if (stocked) {
                userStockFormat(dataItems.getItems(), SessionCtx.getCurrentUser().getUserId());
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.POST)
    public ApiPagedResult pageQueryOf() {
        try {
            Map<String, Object> requestBody = this.getRequestBody();
            String appId = Objects.toString(requestBody.get("appId"), "");
            String tenantCode = Objects.toString(requestBody.get("tenantCode"), "");
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, requestBody, true);
            return userService.pageQueryModelOf(filterGroup, pageQueryRequest, appId, tenantCode);
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
            List<User> list = userService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            return ApiResult.success(userFormat(list));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryOrgUserByUserId", method = RequestMethod.POST)
    public ApiResult queryOrgUserByUserId(@RequestBody Map<String, Object> params) {
        try {
            if (params == null || params.isEmpty()) {
                throw new RuntimeException("Params is null");
            }
            return ApiResult.success(userService.queryOrgUserByUserId(params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/queryByParams", method = RequestMethod.POST)
    public ApiResult query(@RequestBody Map<String, Object> params) {
        try {
            if (params == null || params.isEmpty()) {
                throw new RuntimeException("Params is null");
            }
            FilterGroup filterGroup = new FilterGroup();
            filterGroup.addFilter("id", FilterGroup.Operator.in, String.valueOf(params.get("ids")));
            List<User> list = userService.queryModel(CLAZZ, filterGroup);
            return ApiResult.success(userFormat(list));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult get(@PathVariable(required = true) String id) {
        try {
            User model = userService.getModel(CLAZZ, id);
            return ApiResult.success(userFormat(model));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult createOrUpdate(@RequestBody User form) {
        try {
            User uMap = new User();
            // 组织
            setUserOrg(form);
            // 组织ID为空方可插入
            if (Strings.isNotBlank(form.getId())) {
                // 组织存在，方可更新
                User user = userService.getModel(CLAZZ, form.getId());
                if (user != null) {
                    form.setPassword(user.getPassword());
                    form.setSalt(user.getSalt());
                    uMap = userService.updateModel(form);
                } else {
                    throw new RuntimeException("User is not exist");
                }
            } else {
                form.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
                EncryptUtil.encryptPassword(form);
                uMap = userService.createModel(form);
                uMap.setPlainPassword(form.getPlainPassword());
            }
            // 设置默认组织
            userService.setDefaultOrg(JSON.parseObject(JSON.toJSONString(uMap), User.class));
            // 不返回密码
            uMap.setSalt(null);
            uMap.setPassword(null);
            return ApiResult.success(uMap);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    public ApiResult insert(@RequestBody User form) {
        try {
            User uMap = new User();
            if (Strings.isNotBlank(form.getLoginName())) {
                Map<String, Object> params = new HashMap<>();
                params.put("loginName", form.getLoginName());
                List<User> users = userService.queryModel(User.class, params);
                if (users != null && !users.isEmpty()) {
                    throw new RuntimeException("用户已存在！");
                }
            } else {
                throw new RuntimeException("登录名不能为空！");
            }
            // 组织
            setUserOrg(form);
            // 组织ID为空方可插入
            form.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
            EncryptUtil.encryptPassword(form);
            // 创建用户
            uMap = userService.createModel(form);
            uMap.setPlainPassword(form.getPlainPassword());
            // 设置默认组织
            userService.setDefaultOrg(JSON.parseObject(JSON.toJSONString(uMap), User.class));
            // 不返回密码
            uMap.setSalt(null);
            uMap.setPassword(null);
            return ApiResult.success(uMap);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private void setUserOrg(User form) {
        if (Strings.isNotBlank(form.getOrgId())) {
            Org oForm = orgService.getModel(Org.class, form.getOrgId());
            if (oForm != null) {
                form.setOrgName(oForm.getName());
                form.setDeptId(oForm.getId());
                Org cForm = orgService.getCompany(oForm.getId());
                form.setBuId(cForm == null ? null : cForm.getId());
            } else {
                form.setOrgId(null);
            }
        }
        if (Strings.isBlank(form.getOrgId())) {
            form.setOrgId(null);
            form.setOrgName(null);
            form.setBuId(null);
            form.setDeptId(null);
        }
    }

    @RequestMapping(value = "/resetPwd/{id}", method = RequestMethod.POST)
    public ApiResult resetPassword(@PathVariable(required = true) String id) {
        try {
            User user = userService.getModel(CLAZZ, id);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            user.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
            EncryptUtil.encryptPassword(user);
            userService.updateModel(user);
            return ApiResult.success(user.getPlainPassword());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/resetPush/{id}", method = RequestMethod.POST)
    public ApiResult resetPush(@PathVariable(required = true) String id, String type) {
        try {
            User user = userService.getModel(CLAZZ, id);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            user.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
            EncryptUtil.encryptPassword(user);
            ApiResult result = userService.sendMessage(user, type);
            if (result.isError()) {
                return result;
            }
            userService.updateModel(user);
            return ApiResult.success(user.getPlainPassword());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/sendMessage/{id}", method = RequestMethod.POST)
    public ApiResult sendMessage(@PathVariable(required = true) String id, String type, String message) {
        try {
            User user = userService.getModel(CLAZZ, id);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            return userService.sendMessage(user, type, message);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<NullResult> isDelete(@PathVariable(required = true) String id) {
        try {
            User model = userService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            userService.isDeleteModel(model);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate/{type}", method = RequestMethod.POST)
    public ApiResult<Boolean> validate(@PathVariable(required = true) String type, @RequestBody User form) {
        try {
            if (Strings.isBlank(type)) {
                throw new RuntimeException("The type is not set");
            }
            Map<String, String> params = new HashMap<>();
            if ("loginName".equalsIgnoreCase(type)) {
                params.put("login_name", form.getLoginName());
            } else if ("enName".equalsIgnoreCase(type)) {
                params.put("en_name", form.getEnName());
            } else if ("jobNumber".equalsIgnoreCase(type)) {
                params.put("job_number", form.getJobNumber());
            } else if ("mobilePhone".equalsIgnoreCase(type)) {
                params.put("mobile_phone", form.getMobilePhone());
                params.put("mobile_prefix", form.getMobilePrefix());
            } else {
                return ApiResult.success(true);
            }
            params.put("del_status", String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
            params.put("tenant_code", form.getTenantCode());
            return ApiResult.success(userService.validate("platform_user", form.getId(), params));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/getCompany/{id}", method = RequestMethod.GET)
    public ApiResult getCompany(@PathVariable(required = true) String id) {
        try {
            User user = userService.getModel(User.class, id);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            if (Strings.isBlank(user.getOrgId())) {
                throw new RuntimeException("The user does not set an organization");
            }
            return ApiResult.success(orgService.getCompany(user.getOrgId()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/resetCompany", method = RequestMethod.POST)
    public ApiResult<NullResult> resetCompany() {
        try {
            List<User> users = userService.queryModel(User.class, new HashMap<>());
            if (users != null && users.size() > 0) {
                for (User user : users) {
                    if (Strings.isNotBlank(user.getOrgId())) {
                        Org org = orgService.getCompany(user.getOrgId());
                        user.setBuId(org.getId());
                        user.setDeptId(user.getOrgId());
                    } else {
                        user.setBuId(null);
                        user.setDeptId(null);
                    }
                    orgService.updateModel(user);
                }
            }
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private List<User> userFormat(List<User> models) {
        if (models != null && models.size() > 0) {
            for (User m : models) {
                userFormat(m);
            }
        }
        return models;
    }

    private User userFormat(User model) {
        model.setSalt(null);
        model.setPassword(null);
        model.setPlainPassword(null);
        return model;
    }

    private List<User> userStockFormat(List<User> models, String userId) {
        if (models == null || models.isEmpty() || Strings.isBlank(userId)) {
            return models;
        }
        // 当前查询的用户
        List<String> userIds = models.stream().map(User::getId).collect(Collectors.toList());
        // 查询当前用户的联系人
        FilterGroup filterGroup = new FilterGroup();
        filterGroup.addFilter("userId", userId);
        filterGroup.addFilter("stockId", FilterGroup.Operator.in, StringUtils.join(userIds, ","));
        List<UserStockMap> userStocks = userStockMapService.queryModel(UserStockMap.class, filterGroup);
        if (userStocks == null || userStocks.isEmpty()) {
            return models;
        }
        List<String> stockIds = userStocks.stream().map(UserStockMap::getStockId).collect(Collectors.toList());
        for (User user : models) {
            user.setStocked(stockIds.contains(user.getId()));
        }

        return models;
    }
}
