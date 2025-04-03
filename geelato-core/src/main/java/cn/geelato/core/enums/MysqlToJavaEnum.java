package cn.geelato.core.enums;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

/**
 * @author diabl
 * Mysql数据类型对应的Java对象
 */
@Getter
public enum MysqlToJavaEnum {
    // 字符串
    STRING(String.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.CHAR, MysqlDataTypeEnum.VARCHAR, MysqlDataTypeEnum.TINYTEXT, MysqlDataTypeEnum.TEXT, MysqlDataTypeEnum.MEDIUMTEXT, MysqlDataTypeEnum.LONGTEXT, MysqlDataTypeEnum.JSON}),
    // 布尔值
    BOOLEAN(Boolean.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.BIT}),
    // 整数
    INTEGER(Integer.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYINT, MysqlDataTypeEnum.SMALLINT, MysqlDataTypeEnum.MEDIUMINT, MysqlDataTypeEnum.INT, MysqlDataTypeEnum.INTEGER}),
    LONG(Long.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.BIGINT}),
    // 浮点数
    FLOAT(Float.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.FLOAT}),
    DOUBLE(Double.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.DOUBLE, MysqlDataTypeEnum.DECIMAL}),
    DECIMAL(BigDecimal.class, new MysqlDataTypeEnum[]{}),
    // 时间
    DATE(Date.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.YEAR, MysqlDataTypeEnum.DATE, MysqlDataTypeEnum.DATETIME}),
    TIME(Time.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TIME}),
    TIMESTAMP(Timestamp.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TIMESTAMP}),

    SET(Set.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.SET}),
    ENUM(Enum.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.ENUM}),

    BYTE(Byte.class, new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYBLOB, MysqlDataTypeEnum.BLOB, MysqlDataTypeEnum.MEDIUMBLOB, MysqlDataTypeEnum.LONGBLOB});

    private final Class java;
    private final MysqlDataTypeEnum[] mysql;

    MysqlToJavaEnum(Class java, MysqlDataTypeEnum[] mysql) {
        this.java = java;
        this.mysql = mysql;
    }

    /**
     * 获取数据库数据类型对应的Java对象类型。
     * 根据给定的数据库数据类型名称，返回对应的Java对象类型。
     *
     * @param type 数据库数据类型名称
     * @return 返回对应的Java对象类型，如果未找到对应的类型，则返回null
     */
    public static Class getJava(String type) {
        if (Strings.isNotBlank(type)) {
            for (MysqlToJavaEnum value : MysqlToJavaEnum.values()) {
                if (Arrays.asList(value.getMysql()).contains(MysqlDataTypeEnum.getEnum(type))) {
                    return value.getJava();
                }
            }
        }

        return null;
    }
}
