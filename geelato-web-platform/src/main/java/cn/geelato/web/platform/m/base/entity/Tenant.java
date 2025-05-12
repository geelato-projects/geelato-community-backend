package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_tenant",catalog = "platform")
@Title(title = "租户站点")
public class Tenant extends BaseEntity {
    @Title(title = "企业的编码即租户编码")
    private String code;
    @Title(title = "公司名称")
    @Col(name = "company_name")
    private String companyName;
    @Title(title = "公司域名")
    @Col(name = "company_domain")
    private String companyDomain;
    @Title(title = "公司邮箱")
    @Col(name = "main_email")
    private String mainEmail;
    @Title(title = "公司邮箱密码")
    @Col(name = "main_email_pwd")
    private String mainEmailPwd;
    @Title(title = "公司corpId")
    @Col(name = "corp_id")
    private String corpId;
    @Title(title = "公司corpToken")
    @Col(name = "corp_token")
    private String corpToken;
}
