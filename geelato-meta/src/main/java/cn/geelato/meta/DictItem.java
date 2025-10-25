package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.lang.meta.Transient;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Title(title = "数据字典项")
@Entity(name = "platform_dict_item", catalog = "platform")
@TableName("platform_dict_item")
public class DictItem extends BaseSortableEntity implements EntityEnableAble {

    @Title(title = "应用Id")
    @Col(name = "app_id")
    private String appId;
    private String pid;
    @Col(name = "dict_id")
    @Title(title = "字典ID")
    private String dictId;
    @Col(name = "item_code")
    @Title(title = "字典项编码")
    private String itemCode;
    @Col(name = "item_name")
    @Title(title = "字典项文本")
    private String itemName;
    @Col(name = "item_name_en")
    @Title(title = "英文名称")
    private String itemNameEn;
    @Col(name = "item_color")
    @Title(title = "字典项颜色")
    private String itemColor;
    @Col(name = "item_tag")
    @Title(title = "字典项标签")
    private String itemTag;
    @Col(name = "item_remark")
    @Title(title = "描述")
    private String itemRemark;
    @Col(name = "enable_status")
    @Title(title = "启用状态")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Col(name = "item_extra")
    @Title(title = "扩展字段")
    private String itemExtra;

    @Transient
    private List<DictItem> children;
}
