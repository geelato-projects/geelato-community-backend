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

    @Title(title = "用于指定该站点配置信息绑定的域名的端口号，格式如8080")
    private String port;

    @Title(title = "启动多语言")
    @Col(name = "enable_mutil_lang")
    private boolean enableMutilLang = false;

    @Title(title = "单点登录地址")
    @Col(name = "sso_url")
    private String ssoUrl;

    @Title(title = "微信开发平台应用回调的域名，格式如www.xxx.com")
    @Col(name = "wechat_domain")
    private String wechatDomain;

    @Title(title = "后端接口地址")
    @Col(name = "main_url")
    private String mainUrl;

    @Title(title = "忘记密码页面地址")
    @Col(name = "forget_password_url")
    private String forgetPasswordUrl;

    @Title(title = "微信登录")
    @Col(name = "wechat_info")
    private String wechatInfo;

    @Title(title = "钉钉登录")
    @Col(name = "dingding_info")
    private String dingdingInfo;

    @Title(title = "飞书登录")
    @Col(name = "feishu_info")
    private String feishuInfo;
}
