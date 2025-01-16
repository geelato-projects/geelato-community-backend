package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_tenant_site")
@Title(title = "租户站点")
public class TenantSite extends BaseEntity {

    @Title(title = "一般可填写企业的名称")
    private String name;

    @Title(title = "一般可填写企业的名称")
    private String copyright;

    @Title(title = "网安备案号")
    @Col(name = "ns_filing_no")
    private String nsFilingNo;

    @Title(title = "企业的logo图标，一般为不带文字正方形或圆形的小图标")
    @Col(name = "logo_icon")
    private String logoIcon;

    @Title(title = "特点、亮点")
    @Col(name = "features", dataType = "JSON")
    private String features;

    @Title(title = "企业的logo，一般带文字的版本，区别于标志图标。")
    private String logo;

    @Title(title = "ICP备案号")
    @Col(name = "icp_filing_no")
    private String icpFilingNo;

    @Title(title = "语言，如cn（中文）、en（英文）")
    private String lang;

    @Title(title = "欢迎语")
    private String welcome;

    @Title(title = "网站的口号标语，可用于首页的title中")
    private String slogan;

    @Title(title = "用于指定该站点配置信息绑定的域名，格式如www.xxx.com")
    private String domain;

    @Title(title = "单点登录域名，格式如www.xxx.com", description = "与微信开发平台应用回调的域名一致")
    @Col(name = "sso_domain")
    private String ssoDomain;

    @Title(title = "用于指定该站点配置信息绑定的域名的端口号，格式如8080")
    private String port;

    @Title(title = "启动多语言")
    @Col(name = "enable_mutil_lang")
    private boolean enableMutilLang = false;
}
