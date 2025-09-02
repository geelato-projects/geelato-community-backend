package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.lang.meta.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_dict",catalog = "platform")
@Title(title = "数据字典")
public class Dict extends BaseSortableEntity implements EntityEnableAble {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "字典名称")
    @Col(name = "dict_name")
    private String dictName;
    @Title(title = "字典英文名称")
    @Col(name = "dict_name_en")
    private String dictNameEn;
    @Title(title = "字典编码")
    @Col(name = "dict_code")
    private String dictCode;
    @Title(title = "字典颜色")
    @Col(name = "dict_color")
    private String dictColor;
    @Title(title = "字典备注")
    @Col(name = "dict_remark")
    private String dictRemark;
    @Title(title = "启用状态")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Col(name = "extra_name")
    @Title(title = "扩展字段")
    private String extraName;

    @Col(name = "extra_value_type")
    @Title(title = "扩展字段值")
    private String extraValueType;

    @Col(name = "extra_content")
    @Title(title = "扩展字段内容")
    private String extraContent;

    @Transient
    private Set<DictItem> dictItems = new LinkedHashSet<>();
}
