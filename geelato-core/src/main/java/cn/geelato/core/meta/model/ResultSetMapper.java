package cn.geelato.core.meta.model;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
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
public class ResultSetMapper<T> {
    public List<T> mapResultSetToObject(ResultSet rs, Class outputClass) {
        List<T> outputList = null;
        try {
            // make sure resultset is not null
            if (rs != null) {
                // check if outputClass has 'Entity' annotation
                if (outputClass.isAnnotationPresent(Entity.class)) {
                    // getBizRuleScriptManager the resultset metadata
                    ResultSetMetaData rsmd = rs.getMetaData();
                    // getBizRuleScriptManager all the attributes of outputClass
                    Field[] fields = outputClass.getDeclaredFields();
                    while (rs.next()) {
                        T bean = (T) outputClass.newInstance();
                        for (int _iterator = 0; _iterator < rsmd.getColumnCount(); _iterator++) {
                            // getting the SQL column name
                            String columnName = rsmd.getColumnName(_iterator + 1);
                            // reading the value of the SQL column
                            Object columnValue = rs.getObject(_iterator + 1);
                            // iterating over outputClass attributes to check if
                            // any attribute has 'Column' annotation with
                            // matching 'name' value
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
            e.printStackTrace();
        }
        return outputList;
    }
}