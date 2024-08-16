package cn.geelato.core.meta.schema;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author diabl
 * @description: 数据库中表单信息
 * SELECT * FROM information_schema.tables WHERE 1 = 1 AND TABLE_SCHEMA = '' AND TABLE_TYPE = 'BASE TABLE';
 * @date 2023/6/16 11:44
 */
@Getter
@Setter
public class SchemaTable implements Serializable {
    private String tableCatalog;
    private String tableSchema;
    private String tableName;
    private String tableType;
    private String engine;
    private String version;
    private String rowFormat;
    private String tableRows;
    private String avgRowLength;
    private String dataLength;
    private String maxDataLength;
    private String indexLength;
    private String dataFree;
    private String autoIncrement;
    private String createTime;
    private String updateTime;
    private String checkTime;
    private String tableCollation;
    private String checksum;
    private String createOptions;
    private String tableComment;
}
