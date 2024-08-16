package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.annotation.Transient;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import lombok.Setter;

import java.util.List;

/**
 * @author liuwq
 * @Date 2020/4/10 18:12
 */
@Setter
@Entity(name = "platform_dict_item")
@Title(title = "数据字典项")
public class DictItem extends BaseSortableEntity implements EntityEnableAble {

    private String appId;
    private String pid;
    private String dictId;
    private String itemCode;
    private String itemName;
    private String itemRemark;
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
    private List<DictItem> children;


    @Title(title = "应用Id")
    @Col(name = "app_id")
    public String getAppId() {
        return appId;
    }

    @Col(name = "pid")
    @Title(title = "字典项父级")
    public String getPid() {
        return pid;
    }

    @Col(name = "dict_id")
    @Title(title = "字典ID")
    public String getDictId() {
        return dictId;
    }

    @Col(name = "item_code")
    @Title(title = "字典项编码")
    public String getItemCode() {
        return itemCode;
    }

    @Col(name = "item_name")
    @Title(title = "字典项文本")
    public String getItemName() {
        return itemName;
    }

    @Col(name = "item_remark")
    @Title(title = "描述")
    public String getItemRemark() {
        return itemRemark;
    }

    @Col(name = "enable_status")
    @Title(title = "启用状态")
    @Override
    public int getEnableStatus() {
        return enableStatus;
    }

    @Title(title = "字典项子集")
    @Transient
    public List<DictItem> getChildren() {
        return children;
    }
}
