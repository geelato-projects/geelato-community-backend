package cn.geelato.web.platform.m.security.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.ForeignKey;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by hongxq
 *
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_org_r_user")
@Title(title = "组织用户关系表")
public class OrgUserMap extends BaseEntity {
    @Title(title = "组织ID")
    @ForeignKey(fTable = Org.class)
    @Col(name = "org_id")
    private String orgId;
    @Title(title = "组织名称")
    @ForeignKey(fTable = Org.class)
    @Col(name = "org_name")
    private String orgName;
    @Title(title = "用户ID")
    @ForeignKey(fTable = User.class)
    @Col(name = "user_id")
    private String userId;
    @Title(title = "用户名称")
    @ForeignKey(fTable = User.class)
    @Col(name = "user_name")
    private String userName;
    @Title(title = "1-默认组织 0-兼职")
    @Col(name = "default_org")
    private int defaultOrg;
}
