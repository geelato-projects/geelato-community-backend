package cn.geelato.orm.querydsl;

import java.io.ByteArrayInputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;

public class DefaultSqlExecutorHelper {
    protected static void preparedStatementParameter(PreparedStatement statement, Object[] parameter) throws SQLException {
        if (parameter == null || parameter.length == 0) {
            return;
        }
        int index = 1;
        //预编译参数
        for (Object object : parameter) {
            if (object == null) {
                statement.setNull(index++, Types.NULL);
            } else if (object instanceof NullValue) {
                statement.setNull(index++, ((NullValue) object).getDataType().getSqlType().getVendorTypeNumber());
            } else if (object instanceof Date) {
                statement.setTimestamp(index++, new java.sql.Timestamp(((Date) object).getTime()));
            } else if (object instanceof byte[]) {
                statement.setBlob(index++, new ByteArrayInputStream((byte[]) object));
            } else{
                statement.setObject(index++, object);
            }

        }
    }
}
