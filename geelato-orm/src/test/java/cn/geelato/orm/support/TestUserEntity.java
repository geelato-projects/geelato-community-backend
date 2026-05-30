package cn.geelato.orm.support;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;

@Entity(name = "TestUser", table = "test_user")
public class TestUserEntity {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;

    @Col(name = "del_status", dataType = "INT")
    private Integer delStatus;

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
