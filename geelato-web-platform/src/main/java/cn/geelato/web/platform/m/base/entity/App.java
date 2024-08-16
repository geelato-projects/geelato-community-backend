package cn.geelato.web.platform.m.base.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Setter;

/**
 * @author itechgee@126.com
 * @date 2017/9/8.
 */
@Setter
@Entity(name = "platform_app", table = "platform_app")
@Title(title = "应用")
public class App extends BaseSortableEntity {

    private String name;// 应用名称
    private String code;// 应用编码
    private String type;// 应用分类
    private String icon;// 图标
    private String appKey;
    private String token;
    private String tree;
    private String logo;// 标识
    private String theme;
    private int watermark = 0;// 应用水印
    private String href;// 首页链接
    private String dependAppCode;
    private String powerInfo;
    private String versionInfo;
    private String description;// 描述
    private int applyStatus = 1;
    private int designStatus = 1;
    private String roles;
    private String connects;
    private String appStorage;

    private String purpose;// 使用范围

    @Col(name = "name", nullable = false)
    @Title(title = "应用名称")
    public String getName() {
        return name;
    }

    @Col(name = "code", unique = true)
    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    @Col(name = "type")
    @Title(title = "应用分类")
    public String getType() {
        return type;
    }

    @Col(name = "icon")
    @Title(title = "图标")
    public String getIcon() {
        return icon;
    }

    @Col(name = "logo")
    @Title(title = "Logo")
    public String getLogo() {
        return logo;
    }

    @Col(name = "theme")
    @Title(title = "应用主题")
    public String getTheme() {
        return theme;
    }

    @Col(name = "watermark")
    @Title(title = "应用水印", description = "是否启用应用水印，默认不启用。")
    public int getWatermark() {
        return watermark;
    }

    @Col(name = "app_key")
    @Title(title = "应用秘钥")
    public String getAppKey() {
        return appKey;
    }

    @Col(name = "token")
    @Title(title = "应用Token")
    public String getToken() {
        return token;
    }

    @Col(name = "href")
    @Title(title = "首页链接", description = "加载模块之后打开的首页面")
    public String getHref() {
        return href;
    }

    @Col(name = "tree", nullable = false, dataType = "longText")
    @Title(title = "文件树", description = "json字符串，如jsTree")
    public String getTree() {
        return tree;
    }

    @Col(name = "depend_app_code")
    @Title(title = "依赖的应用", description = "依赖的应用模块编码，可多个，格式如：dev,sys")
    public String getDependAppCode() {
        return dependAppCode;
    }

    @Col(name = "power_info")
    @Title(title = "权限信息", description = "页面底部的网站说明")
    public String getPowerInfo() {
        return powerInfo;
    }

    @Col(name = "version_info")
    @Title(title = "版本信息")
    public String getVersionInfo() {
        return versionInfo;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    @Col(name = "apply_status")
    @Title(title = "应用站点状态", description = "1:启用；0:禁用")
    public int getApplyStatus() {
        return applyStatus;
    }

    @Col(name = "design_status")
    @Title(title = "设计站点状态", description = "1:启用；0:禁用")
    public int getDesignStatus() {
        return designStatus;
    }

    @Transient
    public String getRoles() {
        return roles;
    }

    @Transient
    public String getConnects() {
        return connects;
    }

    @Col(name = "app_storage")
    @Title(title = "应用仓库地址")
    public String getAppStorage() {
        return appStorage;
    }

    @Col(name = "purpose")
    @Title(title = "应用范围")
    public String getPurpose() {
        return purpose;
    }
}
