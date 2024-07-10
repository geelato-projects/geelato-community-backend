package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.Org;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.AccountService;
import cn.geelato.web.platform.m.security.service.OrgService;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.api.ApiPagedResult;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.constants.ApiErrorMsg;
import cn.geelato.core.constants.ApiResultStatus;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.util.UUIDUtils;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.security.entity.DataItems;
import cn.geelato.web.platform.m.security.service.UserService;
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
@RequestMapping(value = "/api/security/user")
public class UserRestController extends BaseController {
    private static final String DEFAULT_PASSWORD = "12345678";
    private static final int DEFAULT_PASSWORD_DIGIT = 8;
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<User> CLAZZ = User.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("name", "loginName", "orgName", "description"));
        OPERATORMAP.put("consists", Arrays.asList("orgId"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final Logger logger = LoggerFactory.getLogger(UserRestController.class);
    @Autowired
    protected AccountService accountService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrgService orgService;


    @RequestMapping(value = "/pageQuery", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQuery(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = userService.pageQueryModel(CLAZZ, filterGroup, pageQueryRequest);
            DataItems<List<User>> dataItems = (DataItems<List<User>>) result.getData();
            userFormat(dataItems.getItems());
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQueryOf(HttpServletRequest req, String appId, String tenantCode) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            FilterGroup filterGroup = this.getFilterGroup(CLAZZ, req, OPERATORMAP);
            result = userService.pageQueryModelOf(filterGroup, pageQueryRequest, appId, tenantCode);
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
            List<User> list = userService.queryModel(CLAZZ, params, pageQueryRequest.getOrderBy());
            result.setData(userFormat(list));
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
                List<User> list = userService.queryModel(CLAZZ, filterGroup);
                return result.setData(userFormat(list));
            }
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
            User model = userService.getModel(CLAZZ, id);
            result.setData(userFormat(model));
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult createOrUpdate(@RequestBody User form) {
        ApiResult result = new ApiResult();
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
                    result.error().setMsg(ApiErrorMsg.IS_NULL);
                }
            } else {
                form.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
                accountService.entryptPassword(form);
                uMap = userService.createModel(form);
                uMap.setPlainPassword(form.getPlainPassword());
            }
            if (ApiResultStatus.SUCCESS.equals(result.getStatus())) {
                userService.setDefaultOrg(JSON.parseObject(JSON.toJSONString(uMap), User.class));
            }
            uMap.setSalt(null);
            uMap.setPassword(null);
            result.setData(uMap);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/insert", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult insert(@RequestBody User form) {
        ApiResult result = new ApiResult();
        try {
            User uMap = new User();
            if (Strings.isNotBlank(form.getLoginName())) {
                Map<String, Object> params = new HashMap<>();
                params.put("loginName", form.getLoginName());
                List<User> users = userService.queryModel(User.class, params);
                if (users != null && users.size() > 0) {
                    return result.error().setMsg("用户已创建").setData(users.get(0));
                }
            } else {
                return result.error().setMsg("登录名不能为空！");
            }
            // 组织
            setUserOrg(form);
            // 组织ID为空方可插入
            form.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
            accountService.entryptPassword(form);
            // 创建用户
            uMap = userService.createModel(form);
            uMap.setPlainPassword(form.getPlainPassword());
            if (ApiResultStatus.SUCCESS.equals(result.getStatus())) {
                userService.setDefaultOrg(JSON.parseObject(JSON.toJSONString(uMap), User.class));
            }
            uMap.setSalt(null);
            uMap.setPassword(null);
            result.setData(uMap);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
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
    @ResponseBody
    public ApiResult resetPassword(@PathVariable(required = true) String id) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(id)) {
                User user = userService.getModel(CLAZZ, id);
                Assert.notNull(user, ApiErrorMsg.IS_NULL);
                user.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
                accountService.entryptPassword(user);
                userService.updateModel(user);
                result.setData(user.getPlainPassword());
            } else {
                result.error().setMsg(ApiErrorMsg.ID_IS_NULL);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/resetPush/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult resetPush(@PathVariable(required = true) String id, String type) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(id)) {
                User user = userService.getModel(CLAZZ, id);
                Assert.notNull(user, ApiErrorMsg.IS_NULL);
                user.setPlainPassword(UUIDUtils.generatePassword(DEFAULT_PASSWORD_DIGIT));
                accountService.entryptPassword(user);
                result = userService.sendMessage(user, type);
                if (result.isError()) {
                    return result;
                }
                userService.updateModel(user);
                result.setData(user.getPlainPassword());
            } else {
                result.error().setMsg(ApiErrorMsg.ID_IS_NULL);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/sendMessage/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult sendMessage(@PathVariable(required = true) String id, String type, String message) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(id)) {
                User user = userService.getModel(CLAZZ, id);
                Assert.notNull(user, ApiErrorMsg.IS_NULL);
                result = userService.sendMessage(user, type, message);
            } else {
                result.error().setMsg(ApiErrorMsg.ID_IS_NULL);
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
            User model = userService.getModel(CLAZZ, id);
            Assert.notNull(model, ApiErrorMsg.IS_NULL);
            userService.isDeleteModel(model);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.DELETE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate/{type}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult validate(@PathVariable(required = true) String type, @RequestBody User form) {
        ApiResult result = new ApiResult();
        try {
            if (Strings.isNotBlank(type)) {
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
                    return result.setData(true);
                }
                params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
                params.put("tenant_code", form.getTenantCode());
                result.setData(userService.validate("platform_user", form.getId(), params));
            } else {
                result.error().setMsg(ApiErrorMsg.PARAMETER_MISSING);
            }
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
            User user = userService.getModel(User.class, id);
            if (user != null && Strings.isNotBlank(user.getOrgId())) {
                result.setData(orgService.getCompany(user.getOrgId()));
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.QUERY_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/resetCompany", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult resetCompany(HttpServletRequest req) {
        ApiResult result = new ApiResult();
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
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
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
}
