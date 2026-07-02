package cn.geelato.web.oss;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * OSS Bucket 统计信息
 */
@Getter
@Setter
@Accessors(chain = true)
public class OSSBucketStats {
    /**
     * Bucket 名称
     */
    private String bucketName;
    /**
     * 区域
     */
    private String region;
    /**
     * 总存储占用（字节）
     */
    private long totalStorage;
    /**
     * 文件总数
     */
    private long fileCount;
    /**
     * 最后修改时间
     */
    private Date lastModified;
}
