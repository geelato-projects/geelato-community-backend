package cn.geelato.core.meta.model.entity;

import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author geemeta
 */
@Getter
@Setter
public abstract class IdEntity implements Serializable {

    @Id
    @Title(title = "序号")
    @TableField(fill = FieldFill.INSERT)
    protected String id;

}
