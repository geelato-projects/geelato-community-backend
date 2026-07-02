package cn.geelato.web.oss;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class OSSFile {
    private String objectId;
    private String objectName;
    private FileMeta fileMeta;
}
