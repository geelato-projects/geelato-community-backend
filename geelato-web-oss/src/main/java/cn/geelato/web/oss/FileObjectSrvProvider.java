package cn.geelato.web.oss;

import java.util.List;

public interface FileObjectSrvProvider {
    OSSResult putFile(FileMeta fileMeta);
    OSSResult getFile(String objectName);
    OSSResult getFiles(List<String> objectNameList);
    OSSResult removeFile(String objectName);
    OSSResult removeFiles(List<String> objectNameList);
    OSSResult getBucketStats();

    /**
     * 检查 OSS 对象是否存在
     */
    boolean objectExists(String objectName);
}
