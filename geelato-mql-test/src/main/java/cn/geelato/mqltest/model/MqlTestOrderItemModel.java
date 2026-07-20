package cn.geelato.mqltest.model;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：订单明细（运行时用）。
 */
@Entity(name = "mql_test_order_item", table = "mql_test_order_item")
public class MqlTestOrderItemModel {
    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;
    @ForeignKey(fTable = MqlTestOrderModel.class)
    @Col(name = "order_id", dataType = "BIGINT")
    private String orderId;
    @Col(name = "product_name", dataType = "VARCHAR", charMaxlength = 128)
    private String productName;
    @Col(name = "qty", dataType = "INT")
    private Integer qty;
    @Col(name = "price", dataType = "DECIMAL", numericPrecision = 12, numericScale = 2)
    private java.math.BigDecimal price;
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
