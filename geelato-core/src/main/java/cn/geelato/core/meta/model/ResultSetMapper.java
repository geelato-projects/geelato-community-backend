package cn.geelato.core.meta.model;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author geemeta
 *
 */
@Slf4j
public class ResultSetMapper<T> {
    public List<T> mapResultSetToObject(ResultSet rs, Class outputClass) {
        List<T> outputList = null;
        try {
            if (rs != null) {
                if (outputClass.isAnnotationPresent(Entity.class)) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    Field[] fields = outputClass.getDeclaredFields();
                    while (rs.next()) {
                        T bean = (T) outputClass.newInstance();
                        for (int _iterator = 0; _iterator < rsmd.getColumnCount(); _iterator++) {
                            String columnName = rsmd.getColumnName(_iterator + 1);
                            Object columnValue = rs.getObject(_iterator + 1);
                            for (Field field : fields) {
                                if (field.isAnnotationPresent(Col.class)) {
                                    Col column = field.getAnnotation(Col.class);
                                    if (column.name().equalsIgnoreCase(columnName) && columnValue != null) {
                                        BeanUtils.setProperty(bean, field.getName(), columnValue);
                                        break;
                                    }
                                }
                            }
                        }
                        if (outputList == null) {
                            outputList = new ArrayList<T>();
                        }
                        outputList.add(bean);
                    }

                } else {
                    // throw some error
                }
            } else {
                return null;
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException | SQLException e) {
            log.error(e.getMessage(), e);
        }
        return outputList;
    }
}