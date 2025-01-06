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
    private String batchNos;
    private String fileName;
    private Integer amount;
    // meta
    @Title(title = "gql语句")
    private String gql;
}
