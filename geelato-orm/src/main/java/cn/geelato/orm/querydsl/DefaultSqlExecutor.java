package cn.geelato.orm.querydsl;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.function.Predicate;


public abstract class DefaultSqlExecutor {
    private Logger logger;
    public <T, R> R doSelect(Connection connection, SqlRequest request, ResultWrapper<T, R> wrapper) {
        return doSelect(logger, connection, request, wrapper, (t) -> false);
    }

    protected <T, R> R doSelect(Logger logger,
                                Connection connection,
                                SqlRequest request,
                                ResultWrapper<T, R> wrapper,
                                Predicate<T> stopped) {
        PreparedStatement statement = connection.prepareStatement(request.getSql());
        try {
            printSql(logger, request);
            preparedStatementParameter(statement, request.getParameters());
            ResultSet resultSet = statement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();
            List<String> columns = getResultColumns(metaData);

            wrapper.beforeWrap(() -> columns);

            int index = 0;
            while (resultSet.next()) {
                //调用包装器,将查询结果包装为对象
                T data = wrapper.newRowInstance();
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i);
                    Object value = getResultValue(metaData, resultSet, i + 1);
                    DefaultColumnWrapperContext<T> context = new DefaultColumnWrapperContext<>(i, column, value, data);
                    wrapper.wrapColumn(context);
                    data = context.getRowInstance();
                }
                index++;
                if (!wrapper.completedWrapRow(data) || stopped.test(data)) {
                    break;
                }
            }
            wrapper.completedWrap();
            logger.debug("==>    Results: {}", index);
            releaseResultSet(resultSet);
            return wrapper.getResult();
        } finally {
            releaseStatement(statement);
        }
    }
}
