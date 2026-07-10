package cn.geelato.core.mql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;

/**
 * @author geemeta
 */
public class TypeConverter {

    private static final HashMap<Class<?>, String> javaTypeToSqlTypeMap;

    private static final HashMap<String, String> sqlTypeToUITypeMap;

    private static HashMap<String, String> isNumericUITypeMap;

    static {
        javaTypeToSqlTypeMap = new HashMap<>();
        //mysql dataType:int|bigint|varchar|datetime|date|time|timestamp
        register(String.class, "varchar");
        register(Byte.class, "blob");
        register(byte.class, "blob");
        register(Short.class, "int");
        register(short.class, "int");
        register(Character.class, "varchar");
        register(char.class, "varchar");
        register(Integer.class, "int");
        register(int.class, "int");
        register(Long.class, "bigint");
        register(long.class, "bigint");
        register(Float.class, "float");
        register(float.class, "float");
        register(Double.class, "double");
        register(double.class, "double");
        register(Boolean.class, "bit");
        register(boolean.class, "bit");
        register(Date.class, "datetime");//to date?
        register(java.sql.Date.class, "datetime");//to date?
        register(Timestamp.class, "timestamp");//to date?
        register(LocalDate.class, "date");
        register(LocalDateTime.class, "datetime");
        register(LocalTime.class, "time");
        register(BigInteger.class, "bigint");
        register(BigDecimal.class, "decimal");
        //TODO db中的json格式

//        NUMBER("number"),
//                STRING("string"),
//                BOOLEAN("boolean"),
//                DATETIME("date");
        sqlTypeToUITypeMap = new HashMap<>();
        register("varchar", "string");
        register("blob", "string");
        register("int", "number");
        register("bigint", "number");
        register("float", "number");
        register("double", "number");
        register("decimal", "number");
        register("bit", "boolean");
        register("datetime", "date");
        register("timestamp", "date");
        register("json", "json");


    }


    private static void register(Class<?> javaType, String sqlType) {
        javaTypeToSqlTypeMap.put(javaType, sqlType);
    }

    /**
     * 类型转换
     *
     * @param javaType java中的类型
     * @return mysql information_schema库中，columns表记录的dataType
     */
    public static String toSqlTypeString(Class<?> javaType) {
        if (!javaTypeToSqlTypeMap.containsKey(javaType)) {
            throw new RuntimeException("未配置转换类型javaType：" + javaType.getName());
        }
        return javaTypeToSqlTypeMap.get(javaType);
    }

    public static int toSqlType(String typeName) {
        if (typeName == null) {
            return Types.VARCHAR;
        }
        return switch (typeName.toLowerCase()) {
            case "int", "integer", "tinyint", "smallint", "mediumint" -> Types.INTEGER;
            case "bigint" -> Types.BIGINT;
            case "float" -> Types.FLOAT;
            case "double" -> Types.DOUBLE;
            case "decimal", "numeric" -> Types.DECIMAL;
            case "bit", "boolean", "bool" -> Types.BOOLEAN;
            case "date" -> Types.DATE;
            case "time" -> Types.TIME;
            case "datetime", "timestamp" -> Types.TIMESTAMP;
            case "blob", "binary", "varbinary", "longblob", "mediumblob", "tinyblob" -> Types.BLOB;
            default -> Types.VARCHAR;
        };
    }

    public static boolean isNumeric(String UIType) {
        return "number".equals(UIType);
    }

    private static void register(String sqlType, String UIType) {
        sqlTypeToUITypeMap.put(sqlType, UIType);
    }

    /**
     * @param sqlType 用于转换的数据库字段类型
     * @return 用于前端UI展示的字段类型
     */
    public static String toUIType(String sqlType) {
        if (!sqlTypeToUITypeMap.containsKey(sqlType)) {
            throw new RuntimeException("未配置转换类型sqlType：" + sqlType);
        }
        return sqlTypeToUITypeMap.get(sqlType);
    }
}
