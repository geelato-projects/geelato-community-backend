package cn.geelato.core.enums;

import cn.geelato.core.meta.model.field.DataTypeRadius;
import org.apache.logging.log4j.util.Strings;

/**
 * @author diabl
 * @description: 数据类型范围
 */
public enum DataTypeRadiusEnum {
    // 字符串，只需要填写最大值。限制字符串填写长度。
    CHAR(MysqlDataTypeEnum.CHAR, new DataTypeRadius(255L, 0L, 3, 3, 0)),
    VARCHAR(MysqlDataTypeEnum.VARCHAR, new DataTypeRadius(21845L, 0L, 5, 5, 0)),
    TEXT(MysqlDataTypeEnum.TEXT, new DataTypeRadius(65535L, 0L, 5, 5, 0)),
    MEDIUMTEXT(MysqlDataTypeEnum.MEDIUMTEXT, new DataTypeRadius(16777215L, 0L, 8, 8, 0)),
    LONGTEXT(MysqlDataTypeEnum.LONGTEXT, new DataTypeRadius(4294967295L, 0L, 10, 10, 0)),
    // 二进制，
    TINYBLOB(MysqlDataTypeEnum.TINYBLOB, new DataTypeRadius(255L, 0L, 10, 10, 0)),
    BLOB(MysqlDataTypeEnum.BLOB, new DataTypeRadius(21845L, 0L, 10, 5, 0)),
    MEDIUMBLOB(MysqlDataTypeEnum.MEDIUMBLOB, new DataTypeRadius(16777216L, 0L, 8, 10, 0)),
    LONGBLOB(MysqlDataTypeEnum.LONGBLOB, new DataTypeRadius(4294967296L, 0L, 10, 10, 0)),
    // 数值，最大值、最小值，限定默认值取值范围。有符号整数位、无符号整数位、小数位。
    TINYINT(MysqlDataTypeEnum.TINYINT, new DataTypeRadius(127L, -128L, 3, 3, 0)),
    SMALLINT(MysqlDataTypeEnum.SMALLINT, new DataTypeRadius(32767L, -32768L, 5, 5, 0)),
    MEDIUMINT(MysqlDataTypeEnum.MEDIUMINT, new DataTypeRadius(8388607L, -8388608L, 7, 8, 0)),
    INT(MysqlDataTypeEnum.INT, new DataTypeRadius(2147483647L, -214748648L, 10, 10, 0)),
    BIGINT(MysqlDataTypeEnum.BIGINT, new DataTypeRadius(9223372036854775807L, -9223372036854775808L, 19, 20, 0)),

    DECIMAL(MysqlDataTypeEnum.DECIMAL, new DataTypeRadius(9223372036854775807L, -9223372036854775808L, 60, 60, 30));

    private final MysqlDataTypeEnum mysql;
    private final DataTypeRadius radius;

    DataTypeRadiusEnum(MysqlDataTypeEnum mysql, DataTypeRadius radius) {
        this.mysql = mysql;
        this.radius = radius;
    }

    /**
     * 获取数据类型范围
     *
     * @param type
     * @return
     */
    public static DataTypeRadius getRadius(String type) {
        if (Strings.isNotBlank(type)) {
            for (DataTypeRadiusEnum value : DataTypeRadiusEnum.values()) {
                if (value.getMysql().equals(MysqlDataTypeEnum.getEnum(type))) {
                    return value.getRadius();
                }
            }
        }
        return null;
    }

    public DataTypeRadius getRadius() {
        return radius;
    }

    public MysqlDataTypeEnum getMysql() {
        return mysql;
    }
}
