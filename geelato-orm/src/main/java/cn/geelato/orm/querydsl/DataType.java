package cn.geelato.orm.querydsl;


import java.sql.JDBCType;
import java.sql.SQLType;

public interface DataType {

    String getId();

    String getName();

    SQLType getSqlType();

    Class<?> getJavaType();

    default boolean isScaleSupport() {
        return getSqlType() == JDBCType.DECIMAL ||
                getSqlType() == JDBCType.DOUBLE ||
                getSqlType() == JDBCType.NUMERIC ||
                getSqlType() == JDBCType.FLOAT;
    }

    default boolean isLengthSupport() {
        return isScaleSupport() ||
                getSqlType() == JDBCType.VARCHAR ||
                getSqlType() == JDBCType.CHAR ||
                getSqlType() == JDBCType.NVARCHAR
                ;
    }

    default boolean isNumber() {
        return DataTypeUtil.typeIsNumber(this);
    }

    default boolean sqlTypeIsNumber() {
        return DataTypeUtil.sqlTypeIsNumber(this.getSqlType());
    }


}
