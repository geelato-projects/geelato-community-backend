package cn.geelato.web.platform.m.file.param;

import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class CompressRequestBody {
    private String serviceType;
    private Date invalidTime;
    private String genre;
    private String attachmentIds;
    private String fileName;
    private Integer amount;
    // meta
    @Title(title = "gql语句")
    private String gql;
    // il_cargo_info_collection
    @Title(title = "所属订单编码", description = "该信息从接口中采集")
    private String orderNo;
    @Title(title = "我司内部的箱号")
    private String ctnNo;
}
