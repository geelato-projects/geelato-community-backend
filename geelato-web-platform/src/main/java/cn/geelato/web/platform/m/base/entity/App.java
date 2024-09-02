package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author itechgee@126.com
 */
@Getter
@Setter
@Entity(name = "platform_app")
@Title(title = "应用")
public class App extends BaseSortableEntity {
    @Title(title = "应用名称")
    private String name;
    @Title(title = "编码")
    private String code;
    @Title(title = "应用分类")
    private String type;
    @Title(title = "图标")
    private String icon;
    @Title(title = "应用秘钥")
    @Col(name = "app_key")
    private String appKey;
    @Title(title = "应用Token")
    private String token;
    @Title(title = "文件树", description = "json字符串，如jsTree")
    private String tree;
    @Title(title = "Logo")
    private String logo;
    @Title(title = "应用主题")
    private String theme;
    @Title(title = "应用水印", description = "是否启用应用水印，默认不启用。")
    private int watermark = 0;
    @Title(title = "首页链接", description = "加载模块之后打开的首页面")
    private String href;
    @Title(title = "依赖的应用", description = "依赖的应用模块编码，可多个，格式如：dev,sys")
    @Col(name = "depend_app_code")
    private String dependAppCode;
    @Title(title = "权限信息", description = "页面底部的网站说明")
    @Col(name = "power_info")
    private String powerInfo;
    @Title(title = "版本信息")
    @Col(name = "version_info")
    private String versionInfo;
    @Title(title = "描述")
    private String description;
    @Title(title = "应用站点状态", description = "1:启用；0:禁用")
    @Col(name = "apply_status")
    private int applyStatus = 1;
    @Title(title = "设计站点状态", description = "1:启用；0:禁用")
    @Col(name = "design_status")
    private int designStatus = 1;
    @Title(title = "应用仓库地址")
    @Col(name = "app_storage")
    private String appStorage;
    @Title(title = "应用范围")
    @Col(name = "purpose")
    private String purpose;

    @Transient
    private String roles;
    @Transient
    private String connects;
}
