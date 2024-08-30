package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
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
@Entity(name = "platform_dict")
@Title(title = "数据字典")
public class Dict extends BaseSortableEntity implements EntityEnableAble {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "字典名称")
    @Col(name = "dict_name")
    private String dictName;
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

    @Transient
    private Set<DictItem> dictItems = new LinkedHashSet<>();
}
