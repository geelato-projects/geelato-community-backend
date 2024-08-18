package cn.geelato.core.meta.model.entity;

import cn.geelato.core.meta.annotation.Id;
import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author geemeta
 */
//@MappedSuperclass
@Getter
@Setter
public abstract class IdEntity implements Serializable {

    @Id
    @Title(title = "序号")
    protected String id;

}
