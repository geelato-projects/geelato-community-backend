package cn.geelato.lang.meta.model.entity;

import cn.geelato.lang.meta.annotation.Id;
import cn.geelato.lang.meta.annotation.Title;

import java.io.Serializable;

/**
 * @author geemeta
 */
//@MappedSuperclass
public abstract class IdEntity implements Serializable {

    protected String id;

    @Id
    @Title(title = "序号")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
