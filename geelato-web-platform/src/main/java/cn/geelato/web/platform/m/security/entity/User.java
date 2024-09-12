package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

/**
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_user")
@Title(title = "用户")
public class User extends BaseSortableEntity implements EntityEnableAble {
    @Title(title = "名称")
    private String name;
    @Title(title = "英文名")
    @Col(name = "en_name")
    private String enName;
    @Title(title = "登录名")
    @Col(name = "login_name", nullable = false)
    private String loginName;
    @Title(title = "性别")
    private int sex;
    @Title(title = "组织")
    @Col(name = "org_id", refTables = "platform_org_r_user,platform_org", refColName = "platform_org.id")
    private String orgId;
    @Title(title = "密码")
    private String password;
    @Title(title = "Salt")
    private String salt;
    @Title(title = "头像")
    private String avatar;
    @Title(title = "手机")
    @Col(name = "mobile_prefix", charMaxlength = 16)
    private String mobilePrefix;
    @Title(title = "手机")
    @Col(name = "mobile_phone", charMaxlength = 16)
    private String mobilePhone;
    @Title(title = "电话")
    private String telephone;
    @Title(title = "邮箱")
    private String email;
    @Title(title = "职务")
    private String post;
    @Title(title = "类型", description = "0:员工账号|1:系统账号|2:企业外人员")
    private int type;
    @Title(title = "来源", description = "0:本地用户|1:系统同步")
    private int source;
    @Title(title = "国家")
    @Col(name = "nation_code")
    private String nationCode;
    @Title(title = "省份")
    @Col(name = "province_code")
    private String provinceCode;
    @Title(title = "城市")
    @Col(name = "city_code")
    private String cityCode;
    @Title(title = "详细地址")
    private String address;
    @Title(title = "描述")
    private String description;
    @Title(title = "部门")
    @Col(name = "orgName", isRefColumn = true, refLocalCol = "orgId", refColName = "platform_org.name")
    private String orgName;
    @Title(title = "工号")
    @Col(name = "job_number")
    private String jobNumber;
    @Title(title = "合作单位id")
    @Col(name = "cooperating_org_id")
    private String cooperatingOrgId;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status", nullable = false, dataType = "tinyint", numericPrecision = 1)
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Title(title = "明文密码", description = "不持久化到数据库，也不显示在Restful接口的属性.")
    @Transient
    private String plainPassword;

    public User() {
    }

    public User(String id) {
        this.setId(id);
    }
}
