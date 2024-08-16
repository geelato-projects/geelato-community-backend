package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

/**
 * Created by hongxq
 * @author diabl
 */
@Setter
@Entity(name = "platform_org_r_user")
@Title(title = "组织用户关系表")
public class OrgUserMap extends BaseEntity {
    private String orgId;
    private String orgName;

    private String userId;
    private String userName;

    //1-默认组织 0-兼职
    private int defaultOrg;

    @Title(title = "组织ID")
    @Col(name = "org_id")
    @ForeignKey(fTable = Org.class)
    public String getOrgId() {
        return orgId;
    }

    @Title(title = "组织名称")
    @Col(name = "org_name")
    @ForeignKey(fTable = Org.class)
    public String getOrgName() {
        return orgName;
    }

    @Title(title = "用户ID")
    @Col(name = "user_id")
    @ForeignKey(fTable = User.class)
    public String getUserId() {
        return userId;
    }

    @Title(title = "用户名称")
    @Col(name = "user_name")
    @ForeignKey(fTable = User.class)
    public String getUserName() {
        return userName;
    }

    @Title(title = "默认组织")
    @Col(name = "default_org")
    public int getDefaultOrg() {
        return defaultOrg;
    }
}
