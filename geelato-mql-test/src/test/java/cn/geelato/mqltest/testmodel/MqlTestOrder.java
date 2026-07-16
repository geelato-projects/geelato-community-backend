package cn.geelato.mqltest.testmodel;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：订单（双外键 user/org、JSON列 tags、保留字列 key/index/enable、枚举状态）
 */
@Entity(name = "mql_test_order", table = "mql_test_order")
public class MqlTestOrder {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Title(title = "订单号")
    @Col(name = "order_no", dataType = "VARCHAR", charMaxlength = 64, unique = true)
    private String orderNo;

    @Title(title = "用户")
    @ForeignKey(fTable = MqlTestUser.class)
    @Col(name = "user_id", dataType = "BIGINT")
    private String userId;

    @Title(title = "机构")
    @ForeignKey(fTable = MqlTestOrg.class)
    @Col(name = "org_id", dataType = "BIGINT")
    private String orgId;

    @Title(title = "金额")
    @Col(name = "amount", dataType = "DECIMAL", numericPrecision = 14, numericScale = 2)
    private java.math.BigDecimal amount;

    @Title(title = "数量")
    @Col(name = "quantity", dataType = "INT")
    private Integer quantity;

    @Title(title = "状态")
    @Col(name = "status", dataType = "VARCHAR", charMaxlength = 32)
    private String status;

    @Title(title = "标签(JSON)")
    @Col(name = "tags", dataType = "JSON")
    private String tags;

    @Title(title = "索引值(保留字列)")
    @Col(name = "index", dataType = "VARCHAR", charMaxlength = 64)
    private String index;

    @Title(title = "键值(保留字列)")
    @Col(name = "key", dataType = "VARCHAR", charMaxlength = 64)
    private String key;

    @Title(title = "使能(保留字列)")
    @Col(name = "enable", dataType = "VARCHAR", charMaxlength = 32)
    private String enable;

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
