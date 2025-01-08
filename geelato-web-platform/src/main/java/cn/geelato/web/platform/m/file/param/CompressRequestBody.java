package cn.geelato.web.platform.m.file.param;

import cn.geelato.core.meta.annotation.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class CompressRequestBody {
    private String serviceType;
    @Title(title = "有效时长", description = "单位：秒")
    private Integer validDuration;
    private String genre;
    private String attachmentIds;
    private String batchNos;
    private String fileName;
    private Integer amount;
    // meta
    @Title(title = "gql语句")
    private String gql;
    @Title(title = "失效时间")
    private Date invalidTime;
}
