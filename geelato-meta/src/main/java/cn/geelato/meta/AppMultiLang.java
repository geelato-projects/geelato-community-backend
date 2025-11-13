package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_app_multi_lang", catalog = "platform")
@Title(title = "应用多语言")
public class AppMultiLang extends BaseEntity {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "语言类型")
    @Col(name = "lang_type")
    private String langType;
    @Title(title = "语言包")
    @Col(name = "lang_package")
    private String langPackage;
    @Title(title = "语言范围")
    private String purpose;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
}
