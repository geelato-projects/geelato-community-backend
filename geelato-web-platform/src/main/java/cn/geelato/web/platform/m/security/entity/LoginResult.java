package cn.geelato.web.platform.m.security.entity;

import cn.geelato.core.constants.ColumnDefault;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;

/**
 * @author geemeta
 */
@Setter
@Getter
public class LoginResult {
    private String id;
    private String jobNumber;
    private String name;
    private String loginName;
    private String avatar;
    private String mobilePrefix;
    private String mobilePhone;
    private String email;
    private Date registrationDate;
    private String orgId;
    private String orgName;
    private String companyId;
    private String companyName;
    private String nationCode;
    private String provinceCode;
    private String cityCode;
    private String address;
    private String description;
    private String tenantCode;
    private String cooperatingOrgId;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    private String token;
    private String homePath;
    private ArrayList<LoginRoleInfo> roles;

    /**
     * 对象转换
     *
     * @param user
     * @return
     */
    public static LoginResult formatLoginResult(User user) {
        LoginResult loginResult = new LoginResult();
        loginResult.setId(user.getId());
        loginResult.setJobNumber(user.getJobNumber());
        loginResult.setName(user.getName());
        loginResult.setLoginName(user.getLoginName());
        loginResult.setAvatar(user.getAvatar());
        loginResult.setMobilePrefix(user.getMobilePrefix());
        loginResult.setMobilePhone(user.getMobilePhone());
        loginResult.setEmail(user.getEmail());
        loginResult.setRegistrationDate(user.getCreateAt());
        loginResult.setOrgId(user.getOrgId());
        loginResult.setOrgName(user.getOrgName());
        loginResult.setNationCode(user.getNationCode());
        loginResult.setProvinceCode(user.getProvinceCode());
        loginResult.setCityCode(user.getCityCode());
        loginResult.setAddress(user.getAddress());
        loginResult.setDescription(user.getDescription());
        loginResult.setTenantCode(user.getTenantCode());
        loginResult.setCooperatingOrgId(user.getCooperatingOrgId());
        loginResult.setEnableStatus(user.getEnableStatus());
        loginResult.setCompanyId(user.getBuId());

        return loginResult;
    }
}
