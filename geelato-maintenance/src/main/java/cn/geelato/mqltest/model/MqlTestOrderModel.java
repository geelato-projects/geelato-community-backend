package cn.geelato.mqltest.model;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：订单（含 JSON 列、保留字列，运行时用）。
 */
@Entity(name = "mql_test_order", table = "mql_test_order")
public class MqlTestOrderModel {
    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;
    @Col(name = "order_no", dataType = "VARCHAR", charMaxlength = 64, unique = true)
    private String orderNo;
    @ForeignKey(fTable = MqlTestUserModel.class)
    @Col(name = "user_id", dataType = "BIGINT")
    private String userId;
    @ForeignKey(fTable = MqlTestOrgModel.class)
    @Col(name = "org_id", dataType = "BIGINT")
    private String orgId;
    @Col(name = "amount", dataType = "DECIMAL", numericPrecision = 14, numericScale = 2)
    private java.math.BigDecimal amount;
    @Col(name = "quantity", dataType = "INT")
    private Integer quantity;
    @Col(name = "status", dataType = "VARCHAR", charMaxlength = 32)
    private String status;
    @Col(name = "tags", dataType = "JSON")
    private String tags;
    @Col(name = "index", dataType = "VARCHAR", charMaxlength = 64)
    private String index;
    @Col(name = "key", dataType = "VARCHAR", charMaxlength = 64)
    private String key;
    @Col(name = "enable", dataType = "VARCHAR", charMaxlength = 32)
    private String enable;
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
