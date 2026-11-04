package cn.geelato.mqltest.testmodel;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：订单明细（order 外键，多级关联 order→user→org）
 */
@Entity(name = "mql_test_order_item", table = "mql_test_order_item")
public class MqlTestOrderItem {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Title(title = "订单")
    @ForeignKey(fTable = MqlTestOrder.class)
    @Col(name = "order_id", dataType = "BIGINT")
    private String orderId;

    @Title(title = "商品名称")
    @Col(name = "product_name", dataType = "VARCHAR", charMaxlength = 128)
    private String productName;

    @Title(title = "数量")
    @Col(name = "qty", dataType = "INT")
    private Integer qty;

    @Title(title = "单价")
    @Col(name = "price", dataType = "DECIMAL", numericPrecision = 12, numericScale = 2)
    private java.math.BigDecimal price;

    @Title(title = "创建时间")
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;

    @Title(title = "创建人")
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;

    @Title(title = "租户编码")
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
