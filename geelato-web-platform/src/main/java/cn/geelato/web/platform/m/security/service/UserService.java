package cn.geelato.web.platform.m.security.service;

import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.m.base.service.BaseSortableService;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.enums.IsDefaultOrgEnum;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author diabl
 */
@Component
public class UserService extends BaseSortableService {
    private static final String CONFIG_KEY_TEMPLATE_CODE = "mobileTemplateResetPwd";
    private static final Pattern CHINESE_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5]+$");
    private final Logger logger = LoggerFactory.getLogger(UserService.class);
    @Lazy
    @Autowired
    private OrgUserMapService orgUserMapService;
    @Lazy
    @Autowired
    private RoleUserMapService roleUserMapService;
    @Lazy
    @Autowired
    private RoleService roleService;
    @Lazy
    @Autowired
    private EmailService emailService;
    @Lazy
    @Autowired
    private AliMobileService aliMobileService;

    /**
     * 逻辑删除
     *
     * @param model
     */
    public void isDeleteModel(User model) {
        // 用户删除
        model.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        super.isDeleteModel(model);
        // 清理 组织用户表
        Map<String, Object> params = new HashMap<>();
        params.put("userId", model.getId());
        List<OrgUserMap> oList = orgUserMapService.queryModel(OrgUserMap.class, params);
        if (oList != null) {
            for (OrgUserMap oModel : oList) {
                oModel.setDefaultOrg(IsDefaultOrgEnum.NO.getCode());
                orgUserMapService.isDeleteOrgUserMap(oModel);
            }
        }
        // 角色用户关系表
        List<RoleUserMap> rList = roleUserMapService.queryModel(RoleUserMap.class, params);
        if (rList != null) {
            for (RoleUserMap rModel : rList) {
                roleUserMapService.isDeleteModel(rModel);
            }
        }
    }

    public void setDefaultOrg(User model) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", model.getId());
        boolean isExit = false;
        List<OrgUserMap> oList = orgUserMapService.queryModel(OrgUserMap.class, params);
        if (oList != null) {
            for (OrgUserMap oModel : oList) {
                if (oModel.getOrgId() != null && oModel.getOrgId().equals(model.getOrgId())) {
                    isExit = true;
                    if (IsDefaultOrgEnum.IS.getCode() != oModel.getDefaultOrg()) {
                        oModel.setDefaultOrg(IsDefaultOrgEnum.IS.getCode());
                        orgUserMapService.updateModel(oModel);
                    }
                } else if (IsDefaultOrgEnum.IS.getCode() == oModel.getDefaultOrg()) {
                    oModel.setDefaultOrg(IsDefaultOrgEnum.NO.getCode());
                    orgUserMapService.updateModel(oModel);
                }
            }
        }
        String orgId = String.valueOf(model.getOrgId());
        if (!isExit && Strings.isNotBlank(orgId)) {
            OrgUserMap oModel = new OrgUserMap();
            oModel.setUserId(model.getId());
            oModel.setUserName(model.getName());
            oModel.setOrgId(orgId);
            oModel.setOrgName(model.getOrgName());
            oModel.setDefaultOrg(IsDefaultOrgEnum.IS.getCode());
            orgUserMapService.createModel(oModel);
        }
    }


    public ApiPagedResult pageQueryModelOf(FilterGroup filter, PageQueryRequest request, String appId, String tenantCode) {
        ApiPagedResult result = new ApiPagedResult();
        Map<String, Object> resultMap = new HashMap<>();
        // 用户查询
        dao.setDefaultFilter(true, filterGroup);
        List<User> pageQueryList = dao.pageQueryList(User.class, filter, request);
        List<User> queryList = dao.queryList(User.class, filter, request.getOrderBy());
        // 分页结果
        result.setPage(request.getPageNum());
        result.setSize(request.getPageSize());
        result.setTotal(queryList != null ? queryList.size() : 0);
        result.setDataSize(pageQueryList != null ? pageQueryList.size() : 0);
        result.setData(new DataItems(pageQueryList, result.getTotal()));
        List<String> userIds = new ArrayList<>();
        if (pageQueryList != null && pageQueryList.size() > 0) {
            for (User model : pageQueryList) {
                model.setSalt(null);
                model.setPassword(null);
                model.setPlainPassword(null);
                if (!userIds.contains(model.getId())) {
                    userIds.add(model.getId());
                }
            }
        } else {
            return result;
        }
        // 角色查询
        Map<String, Object> params = new HashMap<>();
        params.put("appId", appId);
        params.put("tenantCode", tenantCode);
        List<Role> queryRoles = roleService.queryRoles(params);
        resultMap.put("role", queryRoles);
        List<String> roleIds = new ArrayList<>();
        if (pageQueryList != null && pageQueryList.size() > 0) {
            for (Role model : queryRoles) {
                if (!roleIds.contains(model.getId())) {
                    roleIds.add(model.getId());
                }
            }
        } else {
            return result;
        }
        // 角色用户查询
        List<RoleUserMap> roleUserMaps = roleUserMapService.queryModelByIds(String.join(",", roleIds), String.join(",", userIds));
        List<Map<String, Object>> tableList = new ArrayList<>();
        for (User user : pageQueryList) {
            Map<String, Object> tableParams = JSON.parseObject(JSON.toJSONString(user), Map.class);
            for (Role role : queryRoles) {
                tableParams.put(role.getId(), false);
                if (roleUserMaps != null && roleUserMaps.size() > 0) {
                    for (RoleUserMap map : roleUserMaps) {
                        if (role.getId().equals(map.getRoleId()) && user.getId().equals(map.getUserId())) {
                            tableParams.put(role.getId(), true);
                            break;
                        }
                    }
                }
            }
            tableList.add(tableParams);
        }
        resultMap.put("table", tableList);

        result.setData(new DataItems(resultMap, result.getTotal()));
        return result;
    }

    public ApiResult sendMessage(User user, String type, String message) {
        if (Strings.isNotBlank(message)) {
            user.setPlainPassword(message);
            return sendMessage(user, type);
        } else {
            return ApiResult.fail("发送的信息不能为空！");
        }
    }

    public ApiResult sendMessage(User user, String type) {
        List<String> types = getSendType(type);
        if (types == null || types.size() == 0) {
            return ApiResult.fail("请选择发送方式，短信或邮件！");
        }
        if (types.contains("phone")) {
            if (Strings.isNotBlank(user.getMobilePhone())) {
                boolean pushSuccess = sendMobile(user.getMobilePrefix(), user.getMobilePhone(), user.getName(), user.getPlainPassword());
                if (!pushSuccess) {
                    return ApiResult.fail("短信发送失败，请重试！");
                }
            } else {
                return ApiResult.fail("请补全用户手机信息！");
            }
        }
        if (types.contains("email")) {
            if (Strings.isNotBlank(user.getEmail())) {
                String text = String.format("尊敬的 %s 用户，您的密码已经设置为 %s ，请及时登录并修改密码。", user.getName(), user.getPlainPassword());
                boolean pushSuccess = emailService.sendHtmlMail(user.getEmail(), "Reset User Password", text);
                if (!pushSuccess) {
                    return ApiResult.fail("邮件发送失败，请重试！");
                }
            } else {
                return ApiResult.fail("请补全用户邮箱信息！");
            }
        }

        return ApiResult.successNoResult();
    }

    public boolean sendMobile(String mobilePrefix, String mobilePhone, String name, String password) {
        String phoneNumbers = mobilePhone;
        if (Strings.isNotBlank(mobilePrefix) && !"+86".equals(mobilePrefix)) {
            phoneNumbers = mobilePrefix + phoneNumbers;
        }
        if (!CHINESE_PATTERN.matcher(name).matches()) {
            logger.error("短信${name}仅支持中文。" + name);
            return false;
        }
        try {
            Map<String, Object> params = new HashedMap<>();
            params.put("name", name);
            params.put("password", password);
            return aliMobileService.sendMobile(CONFIG_KEY_TEMPLATE_CODE, phoneNumbers, params);
        } catch (Exception e) {
            logger.error("发送短信时发生异常", e);
        }
        return false;
    }

    public List<String> getSendType(String type) {
        List<String> list = new ArrayList<>();
        if (Strings.isNotBlank(type)) {
            String[] typeStr = type.split(",");
            for (int i = 0; i < typeStr.length; i++) {
                if ("phone".equalsIgnoreCase(typeStr[i]) || "email".equalsIgnoreCase(typeStr[i])) {
                    list.add(typeStr[i].toLowerCase(Locale.ENGLISH));
                }
            }
        }
        return list;
    }
}
