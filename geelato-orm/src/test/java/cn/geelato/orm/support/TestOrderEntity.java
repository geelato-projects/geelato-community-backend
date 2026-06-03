package cn.geelato.orm.support;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;

@Entity(name = "TestOrder", table = "test_order")
public class TestOrderEntity {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @ForeignKey(fTable = TestUserEntity.class)
    @Col(name = "user_id", dataType = "BIGINT")
    private String userId;

    @Col(name = "code", dataType = "VARCHAR", charMaxlength = 64)
    private String code;

    @Col(name = "del_status", dataType = "INT")
    private Integer delStatus;
}
