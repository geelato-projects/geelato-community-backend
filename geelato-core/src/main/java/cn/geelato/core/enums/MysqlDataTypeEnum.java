package cn.geelato.core.enums;

import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author diabl
 * @description: 数据库，数据类型
 * @date 2023/6/21 18:46
 */
public enum MysqlDataTypeEnum {
    // 字符串类型
    CHAR,// 定长字符串，长度在 1 到 255 之间。
    VARCHAR,// 可变长度字符串，长度在 1 到 25535 之间。
    TINYTEXT,//非常小的文本字符串，最大长度为 255 个字符。
    TEXT,//小型文本字符串，最大长度为 65535 个字符。
    MEDIUMTEXT,//中等大小的文本字符串，最大长度为 16777215 个字符。
    LONGTEXT,//大型文本字符串，最大长度为 4294967295 个字符。
    // 布尔值，二进制类型
    BIT,//位字段类型，可以存储位图数据。
    // 二进制数据
    TINYBLOB, // 存储长度小于255字节的二进制数据。
    BLOB, // 存储长度大于等于255字节的二进制数据。
    MEDIUMBLOB, // 存储长度大于等于16777216字节的二进制数据。
    LONGBLOB, // 存储长度大于等于4294967296字节的二进制数据。
    // 数值类型
    TINYINT,//一个非常小的整数。有符号范围：-128至127；无符号范围：0至255。
    SMALLINT,//一个小的整数。有符号范围：-32768至32767；无符号范围：0至65535。
    MEDIUMINT,//一个中等大小的整数。有符号范围：-8388608至8388607；无符号范围：0至16777215。
    INT,//一个普通大小的整数。有符号范围：-2147483648至2147483647；无符号范围：0至4294967295。
    INTEGER,//一个普通大小的整数。有符号范围：-2147483648至2147483647；无符号范围：0至4294967295。
    BIGINT,//一个大的整数。有符号范围：-9223372036854775808至9223372036854775807；无符号范围：0至18446744073709551615。
    FLOAT,//单精度浮点数。
    DOUBLE,//双精度浮点数。
    DECIMAL,//精确小数，用于存储精确的数值，如货币。
    // 日期和时间类型
    YEAR,//年份，可以存储 1000 到 9999 年之间的年份。
    DATE,//日期，格式为 'YYYY-MM-DD'。
    TIME,//时间，格式为 'HH:MM:SS'。
    DATETIME,//日期和时间，格式为 'YYYY-MM-DD HH:MM:SS'。
    TIMESTAMP,//时间戳，格式为 'YYYY-MM-DD HH:MM:SS'，与UNIX时间戳有关。

    JSON,//用于存储和检索结构化数据的 JSON 数据类型。
    SET,//集合类型，允许在表中定义一个可组合的列。
    ENUM// 枚举类型，允许在表中定义一个可列举的列。
    ;

    /**
     * 获取枚举值
     *
     * @param type
     * @return
     */
    public static MysqlDataTypeEnum getEnum(String type) {
        if (Strings.isNotBlank(type)) {
            for (MysqlDataTypeEnum value : MysqlDataTypeEnum.values()) {
                if (value.toString().equals(type.toUpperCase(Locale.ENGLISH))) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 获取，char、varchar
     *
     * @return
     */
    public static List<String> getChars() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.CHAR,
                MysqlDataTypeEnum.VARCHAR};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取text，tinytext、text、mediumtext、longtext
     *
     * @return
     */
    public static List<String> getTexts() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYTEXT,
                MysqlDataTypeEnum.TEXT,
                MysqlDataTypeEnum.MEDIUMTEXT,
                MysqlDataTypeEnum.LONGTEXT};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取 字符串类型，char、varchar、tinytext、text、mediumtext、longtext
     *
     * @return
     */
    public static List<String> getStrings() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.CHAR,
                MysqlDataTypeEnum.VARCHAR,
                MysqlDataTypeEnum.TINYTEXT,
                MysqlDataTypeEnum.TEXT,
                MysqlDataTypeEnum.MEDIUMTEXT,
                MysqlDataTypeEnum.LONGTEXT,
                MysqlDataTypeEnum.JSON};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    public static List<String> getBytes() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYBLOB,
                MysqlDataTypeEnum.BLOB,
                MysqlDataTypeEnum.MEDIUMBLOB,
                MysqlDataTypeEnum.LONGBLOB};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取 布尔值类型，bit
     *
     * @return
     */
    public static List<String> getBooleans() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.BIT};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    public static List<String> getTinyBooleans() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYINT};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取 时间类型，year、date、time、datetime、timestamp
     *
     * @return
     */
    public static List<String> getDates() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.YEAR,
                MysqlDataTypeEnum.DATE,
                MysqlDataTypeEnum.TIME,
                MysqlDataTypeEnum.DATETIME,
                MysqlDataTypeEnum.TIMESTAMP};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取 整数类型
     *
     * @return
     */
    public static List<String> getIntegers() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYINT,
                MysqlDataTypeEnum.SMALLINT,
                MysqlDataTypeEnum.MEDIUMINT,
                MysqlDataTypeEnum.INT,
                MysqlDataTypeEnum.INTEGER,
                MysqlDataTypeEnum.BIGINT};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取，浮点数类型
     *
     * @return
     */
    public static List<String> getDecimals() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.FLOAT,
                MysqlDataTypeEnum.DOUBLE,
                MysqlDataTypeEnum.DECIMAL};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    /**
     * 获取 数值类型
     *
     * @return
     */
    public static List<String> getNumbers() {
        MysqlDataTypeEnum[] typeEnums = new MysqlDataTypeEnum[]{MysqlDataTypeEnum.TINYINT,
                MysqlDataTypeEnum.SMALLINT,
                MysqlDataTypeEnum.MEDIUMINT,
                MysqlDataTypeEnum.INT,
                MysqlDataTypeEnum.INTEGER,
                MysqlDataTypeEnum.BIGINT,
                MysqlDataTypeEnum.FLOAT,
                MysqlDataTypeEnum.DOUBLE,
                MysqlDataTypeEnum.DECIMAL};
        return MysqlDataTypeEnum.getNames(typeEnums);
    }

    private static List<String> getNames(MysqlDataTypeEnum[] typeEnums) {
        List<String> typeNames = new ArrayList<>();
        for (MysqlDataTypeEnum typeEnum : typeEnums) {
            typeNames.add(typeEnum.name());
        }

        return typeNames;
    }
}
