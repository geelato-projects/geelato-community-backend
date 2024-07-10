package cn.geelato.orm.meta.model.entity;

import org.geelato.core.meta.annotation.Id;
import org.geelato.core.meta.annotation.Title;

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
