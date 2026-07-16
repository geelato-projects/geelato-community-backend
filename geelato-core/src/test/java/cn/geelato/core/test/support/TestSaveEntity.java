package cn.geelato.core.test.support;

import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "TestSaveEntity", table = "test_save_entity")
public class TestSaveEntity extends IdEntity {

    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;

    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;

    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;

    @Col(name = "creator_name", dataType = "VARCHAR", charMaxlength = 128)
    private String creatorName;

    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;

    @Col(name = "bu_id", dataType = "VARCHAR", charMaxlength = 64)
    private String buId;

    @Col(name = "dept_id", dataType = "VARCHAR", charMaxlength = 64)
    private String deptId;

    @Col(name = "update_at", dataType = "DATETIME")
    private String updateAt;

    @Col(name = "updater", dataType = "VARCHAR", charMaxlength = 64)
    private String updater;

    @Col(name = "updater_name", dataType = "VARCHAR", charMaxlength = 128)
    private String updaterName;

    @Col(name = "delete_at", dataType = "DATETIME")
    private String deleteAt;
}
