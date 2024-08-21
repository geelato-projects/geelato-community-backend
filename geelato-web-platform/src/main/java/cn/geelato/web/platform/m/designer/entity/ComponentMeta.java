package cn.geelato.web.platform.m.designer.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author geemeta
 */
@Getter
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

    @Col(name = "component_name", nullable = false)
    private String ComponentName;

    private String metaContent;

    private String dependedLibs;
    private String jsFiles;

    private String cssFiles;

    private String vueContent;


}
