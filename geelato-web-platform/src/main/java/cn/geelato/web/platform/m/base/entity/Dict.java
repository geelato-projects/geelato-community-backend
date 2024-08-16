package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author geelato
 */
@Setter
@Entity(name = "platform_dict")
@Title(title = "数据字典")
public class Dict extends BaseSortableEntity implements EntityEnableAble {

    private String appId;
    private String dictCode;
    private String dictName;
    private String dictRemark;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private Set<DictItem> dictItems = new LinkedHashSet<>();

    @Col(name = "dict_code")
    @Title(title = "字典编码")
    public String getDictCode() {
        return dictCode;
    }

    @Col(name = "dict_name")
    @Title(title = "字典名称")
    public String getDictName() {
        return dictName;
    }

    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "dict_remark")
    @Title(title = "字典备注")
    public String getDictRemark() {
        return dictRemark;
    }

    @Col(name = "enable_status")
    @Title(title = "启用状态")
    @Override
    public int getEnableStatus() {
        return this.enableStatus;
    }

    @Transient
    public Set<DictItem> getDictItems() {
        return dictItems;
    }
}
