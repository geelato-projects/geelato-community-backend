package cn.geelato.web.platform.m.designer.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Setter;

/**
 * @author geemeta
 */
@Setter
@Entity(name = "platform_component_meta")
@Title(title = "组件元数据")
public class ComponentMeta extends BaseSortableEntity {

    private String title;
    private String packageName;
    private String alias;
    private String group;
    private String SettingDisplayMode;
    private String SettingPanels;

    private String thumbnail;
    public int publicStatus;
    private String ComponentName;

    private String metaContent;

    private String dependedLibs;
    private String jsFiles;

    private String cssFiles;

    private String vueContent;

    @Title(title = "组件标题")
    public String getTitle() {
        return title;
    }

    @Title(title = "组件名", description = "英文名称")
    @Col(name = "component_name", nullable = false)
    public String getComponentName() {
        return ComponentName;
    }

    @Title(title = "组件包名", description = "组件的命名空间")
    public String getPackageName() {
        return packageName;
    }

    @Title(title = "组件别名", description = "简短的别名，用于创建便于识别的组织维一标识")
    public String getAlias() {
        return alias;
    }

    @Title(title = "组件分组")
    public String getGroup() {
        return group;
    }

    @Title(title = "设置面板模式", description = "组件属性设置面板的展示模式")
    public String getSettingDisplayMode() {
        return SettingDisplayMode;
    }

    @Title(title = "更多设置面板", description = "更多的设置面板名称，多个用逗号分开")
    public String getSettingPanels() {
        return SettingPanels;
    }

    @Title(title = "发布状态", description = "1:已发布；0：待发布")
    public int getPublicStatus() {
        return publicStatus;
    }

    @Title(title = "缩略图", description = "Base64")
    public String getThumbnail() {
        return thumbnail;
    }

    @Title(title = "元数据内容", description = "JSON格式，对组件、组件属性、组件方法进行定义描述")
    public String getMetaContent() {
        return metaContent;
    }

    @Title(title = "JS文件", description = "组件打包后的js文件")
    public String getJsFiles() {
        return jsFiles;
    }

    @Title(title = "CSS文件", description = "组件打包后的css文件")
    public String getCssFiles() {
        return cssFiles;
    }

    @Title(title = "vue单文件组件内容", description = "适用于一些简单一些的，单Vue文件组件")
    public String getVueContent() {
        return vueContent;
    }

    @Title(title = "依赖组件库", description = "如ant.design.vue@2.0")
    public String getDependedLibs() {
        return dependedLibs;
    }
}
