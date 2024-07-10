package cn.geelato.orm.utils;

import cn.geelato.orm.querydsl.NullValue;
import cn.geelato.orm.querydsl.PrepareSqlRequest;
import cn.geelato.orm.querydsl.SqlRequest;
import org.slf4j.Logger;

public class SqlUtil {
    public static void printSql(Logger log, SqlRequest sqlRequest) {
        if (log.isDebugEnabled()) {
            if (sqlRequest.isNotEmpty()) {
                boolean hasParameter = sqlRequest.getParameters() != null && sqlRequest.getParameters().length > 0;

                log.debug("==>  {}: {}", hasParameter ? "Preparing" : "  Execute", sqlRequest.getSql());
                if (hasParameter) {
                    log.debug("==> Parameters: {}", sqlParameterToString(sqlRequest.getParameters()));
                    if (sqlRequest instanceof PrepareSqlRequest) {
//                        log.debug("==>     Native: {}", sqlRequest.toNativeSql());
                    }
                }
            }
        }
    }

    public static String sqlParameterToString(Object[] parameters) {
        if (parameters == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (Object param : parameters) {
            if (i++ != 0) {
                builder.append(",");
            }
            builder.append(param);
            if (!(param instanceof NullValue)) {
                builder.append("(");
                builder.append(param == null ? "null" : param.getClass().getSimpleName());
                builder.append(")");
            }
        }
        return builder.toString();
    }
}
