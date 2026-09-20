package cn.geelato.search.lucene.route;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;

/**
 * 路由器单测实体：so_no/mbl_no 在域字段内，other_no 不在（部分路由用例）。
 * <p>必须 public——MetaReflex 解析时需反射实例化取字段元数据，非 public 实例化失败。
 */
@Entity(name = "srch_route_order", table = "srch_route_order")
public class SrchRouteOrder {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Col(name = "so_no", dataType = "VARCHAR", charMaxlength = 64)
    private String soNo;

    @Col(name = "mbl_no", dataType = "VARCHAR", charMaxlength = 64)
    private String mblNo;

    @Col(name = "other_no", dataType = "VARCHAR", charMaxlength = 64)
    private String otherNo;

    public String getId() {
        return id;
    }

    public String getSoNo() {
        return soNo;
    }

    public String getMblNo() {
        return mblNo;
    }

    public String getOtherNo() {
        return otherNo;
    }
}
