package cn.geelato.web.platform.m.base.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;

/**
 * @author itechgee@126.com
 * @date 2017/9/8.
 */
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

    public void setName(String name) {
        this.name = name;
    }

    @Col(name = "code", unique = true)
    @Title(title = "编码")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Col(name = "type")
    @Title(title = "应用分类")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Col(name = "icon")
    @Title(title = "图标")
    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @Col(name = "logo")
    @Title(title = "Logo")
    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    @Col(name = "theme")
    @Title(title = "应用主题")
    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    @Col(name = "watermark")
    @Title(title = "应用水印", description = "是否启用应用水印，默认不启用。")
    public int getWatermark() {
        return watermark;
    }

    public void setWatermark(int watermark) {
        this.watermark = watermark;
    }

    @Col(name = "app_key")
    @Title(title = "应用秘钥")
    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    @Col(name = "token")
    @Title(title = "应用Token")
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Col(name = "href")
    @Title(title = "首页链接", description = "加载模块之后打开的首页面")
    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    @Col(name = "tree", nullable = false, dataType = "longText")
    @Title(title = "文件树", description = "json字符串，如jsTree")
    public String getTree() {
        return tree;
    }

    public void setTree(String tree) {
        this.tree = tree;
    }


    @Col(name = "depend_app_code")
    @Title(title = "依赖的应用", description = "依赖的应用模块编码，可多个，格式如：dev,sys")
    public String getDependAppCode() {
        return dependAppCode;
    }

    public void setDependAppCode(String dependAppCode) {
        this.dependAppCode = dependAppCode;
    }

    @Col(name = "power_info")
    @Title(title = "权限信息", description = "页面底部的网站说明")
    public String getPowerInfo() {
        return powerInfo;
    }

    public void setPowerInfo(String powerInfo) {
        this.powerInfo = powerInfo;
    }

    @Col(name = "version_info")
    @Title(title = "版本信息")
    public String getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(String versionInfo) {
        this.versionInfo = versionInfo;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Col(name = "apply_status")
    @Title(title = "应用站点状态", description = "1:启用；0:禁用")
    public int getApplyStatus() {
        return applyStatus;
    }

    public void setApplyStatus(int applyStatus) {
        this.applyStatus = applyStatus;
    }

    @Col(name = "design_status")
    @Title(title = "设计站点状态", description = "1:启用；0:禁用")
    public int getDesignStatus() {
        return designStatus;
    }

    public void setDesignStatus(int designStatus) {
        this.designStatus = designStatus;
    }

    @Transient
    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    @Transient
    public String getConnects() {
        return connects;
    }

    public void setConnects(String connects) {
        this.connects = connects;
    }

    @Col(name = "app_storage")
    @Title(title = "应用仓库地址")
    public String getAppStorage() {
        return appStorage;
    }

    public void setAppStorage(String appStorage) {
        this.appStorage = appStorage;
    }

    @Col(name = "purpose")
    @Title(title = "应用范围")
    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
