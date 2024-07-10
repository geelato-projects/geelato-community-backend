package cn.geelato.core.meta.model;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.DateTimeConverter;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Date;

/**
 * @author geemeta
 */
public class CommonRowMapper<T> implements RowMapper<T> {
    private final Log logger = LogFactory.getLog(CommonRowMapper.class);
    private static final MetaManager metaManager = MetaManager.singleInstance();

    public CommonRowMapper() {
        DateTimeConverter dtc = new DateTimeConverter();
        ConvertUtils.register(dtc, java.time.LocalDateTime.class);
        ConvertUtils.register(dtc, Date.class);
        ConvertUtils.register(dtc, java.sql.Date.class);
    }

    @Override
    public T mapRow(ResultSet resultSet, int i) throws SQLException {
        ResultSetMetaData rsmd = resultSet.getMetaData();
        String tableName = rsmd.getTableName(1);
        EntityMeta em = metaManager.get(tableName);

        Converter c = ConvertUtils.lookup(Date.class);
        Converter d = ConvertUtils.lookup(java.sql.Date.class);

        T bean;
        if (em.getClassType() != null) {
            try {
                bean = (T) em.getClassType().newInstance();
                for (int _iterator = 0; _iterator < rsmd.getColumnCount(); _iterator++) {
                    String columnName = rsmd.getColumnName(_iterator + 1);
                    Object columnValue = resultSet.getObject(_iterator + 1);
                    for (FieldMeta fm : em.getFieldMetas()) {
                        if (columnName.equals(fm.getColumnName())) {
                            BeanUtils.setProperty(bean, fm.getFieldName(), columnValue);
                            break;
                        }
                    }
                }
                return bean;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                logger.error(e);
            }
        } else {
            //map?
        }
        return null;
    }
}
