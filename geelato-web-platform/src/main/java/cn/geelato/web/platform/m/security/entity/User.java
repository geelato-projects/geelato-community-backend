package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;

/**
 * @author geelato
 */
@Entity(name = "platform_user")
@Title(title = "用户")
public class User extends BaseSortableEntity implements EntityEnableAble {
    private String name;
    private String enName;
    private String loginName;
    private int sex;
    private String orgId;
    private String password;
    private String salt;
    private String avatar;
    private String plainPassword;
    private String mobilePrefix;
    private String mobilePhone;
    private String telephone;
    private String email;
    private String post;
    private int type;
    private int source;
    private String nationCode;
    private String provinceCode;
    private String cityCode;
    private String address;
    private String description;
    private String orgName;
    private String jobNumber;
    private String cooperatingOrgId;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    public User() {
    }

    public User(String id) {
        this.setId(id);
    }

    @Title(title = "名称")
    @Col(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Title(title = "英文名")
    @Col(name = "en_name")
    public String getEnName() {
        return enName;
    }

    public void setEnName(String enName) {
        this.enName = enName;
    }

    @Title(title = "登录名")
    @Col(name = "login_name", nullable = false)
    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    @Title(title = "组织")
    @Col(name = "org_id", refTables = "platform_org_r_user,platform_org", refColName = "platform_org.id")
    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    @Title(title = "密码")
    @Col(name = "password", nullable = true)
//    @JsonDeserialize(using = JsonIgnoreDeserialize.class)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Title(title = "性别")
    @Col(name = "sex", nullable = false)
    public int getSex() {
        return sex;
    }

    public void setSex(int sex) {
        this.sex = sex;
    }

    @Title(title = "Salt")
    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    @Title(title = "头像")
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    @Title(title = "描述")
    @Col(name = "description", charMaxlength = 1024)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Title(title = "明文密码")
    // 不持久化到数据库，也不显示在Restful接口的属性.
    @Transient
    public String getPlainPassword() {
        return plainPassword;
    }

    public void setPlainPassword(String plainPassword) {
        this.plainPassword = plainPassword;
    }

    @Title(title = "手机")
    @Col(name = "mobile_prefix", charMaxlength = 16)
    public String getMobilePrefix() {
        return mobilePrefix;
    }

    public void setMobilePrefix(String mobilePrefix) {
        this.mobilePrefix = mobilePrefix;
    }

    @Title(title = "手机")
    @Col(name = "mobile_phone", charMaxlength = 16)
    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    @Title(title = "电话")
    @Col(name = "telephone", charMaxlength = 20)
    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    @Title(title = "邮箱")
    @Col(name = "email", charMaxlength = 126)
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Title(title = "职务")
    @Col(name = "post", charMaxlength = 40)
    public String getPost() {
        return post;
    }


    public void setPost(String post) {
        this.post = post;
    }


    @Title(title = "来源", description = "0:本地用户|1:系统同步")
    @Col(name = "source", nullable = false)
    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

    @Title(title = "类型", description = "0:员工账号|1:系统账号|2:企业外人员")
    @Col(name = "type", nullable = false)
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Title(title = "国家")
    @Col(name = "nation_code")
    public String getNationCode() {
        return nationCode;
    }

    public void setNationCode(String nationCode) {
        this.nationCode = nationCode;
    }

    @Title(title = "省份")
    @Col(name = "province_code")
    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    @Title(title = "城市")
    @Col(name = "city_code")
    public String getCityCode() {
        return cityCode;
    }

    public void setCityCode(String cityCode) {
        this.cityCode = cityCode;
    }

    @Title(title = "详细地址")
    @Col(name = "address")
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Title(title = "部门")
    @Col(name = "orgName", isRefColumn = true, refLocalCol = "orgId", refColName = "platform_org.name")
    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    @Title(title = "工号")
    @Col(name = "job_number")
    public String getJobNumber() {
        return jobNumber;
    }

    public void setJobNumber(String jobNumber) {
        this.jobNumber = jobNumber;
    }

    @Title(title = "合作单位id")
    @Col(name = "cooperating_org_id")
    public String getCooperatingOrgId() {
        return cooperatingOrgId;
    }

    public void setCooperatingOrgId(String cooperatingOrgId) {
        this.cooperatingOrgId = cooperatingOrgId;
    }

    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 1)
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Override
    public void setEnableStatus(int enableStatus) {
        this.enableStatus = enableStatus;
    }
}
