package cn.geelato.orm.support;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;

@Entity(name = "TestOrder", table = "test_order")
public class TestOrderEntity {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Col(name = "user_id", dataType = "BIGINT")
    private String userId;

    @Col(name = "code", dataType = "VARCHAR", charMaxlength = 64)
    private String code;
}
