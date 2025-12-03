package cn.geelato.meta;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_app_page_lang")
@Title(title = "页面多语言")
public class AppPageLang extends BaseEntity {
    @Title(title = "所属页面")
    @Col(name = "page_id")
    private String pageId;
    @Title(title = "版本")
    private int version;
    @Title(title = "语言类型")
    @Col(name = "lang_type")
    private String langType;
    @Title(title = "多语言内容")
    private String content;
    @Title(title = "菜单树节点ID")
    @Col(name = "extend_id")
    private String extendId;
}
