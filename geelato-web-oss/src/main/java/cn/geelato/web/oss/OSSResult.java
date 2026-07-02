package cn.geelato.web.oss;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OSSResult {
    private Boolean success;
    private String message;
    private OSSFile ossFile;
    private List<OSSFile> ossFileList;
    private OSSBucketStats bucketStats;
}
