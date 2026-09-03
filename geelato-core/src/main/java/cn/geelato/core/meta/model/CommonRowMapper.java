package cn.geelato.core.meta.model;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.DateTimeConverter;
import cn.geelato.core.util.EncryptUtils;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author geemeta
 */
@Slf4j
@SuppressWarnings("ALL")
public class CommonRowMapper<T> implements RowMapper<T> {
    private static final MetaManager metaManager = MetaManager.singleInstance();

    public CommonRowMapper() {
        DateTimeConverter dtc = new DateTimeConverter();
        ConvertUtils.register(dtc, java.time.LocalDateTime.class);
        ConvertUtils.register(dtc, Date.class);
        ConvertUtils.register(dtc, java.sql.Date.class);
    }

    @Override
    public T mapRow(ResultSet resultSet, int i) throws SQLException {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        String tableName = resultSetMetaData.getTableName(1);
        EntityMeta em = metaManager.get(tableName);

        Converter c = ConvertUtils.lookup(Date.class);
        Converter d = ConvertUtils.lookup(java.sql.Date.class);

        T bean;
        if (em != null && em.getClassType() != null) {
            try {
                bean = (T) em.getClassType().getDeclaredConstructor().newInstance();
                for (int _iterator = 0; _iterator < resultSetMetaData.getColumnCount(); _iterator++) {
                    String columnName = resultSetMetaData.getColumnName(_iterator + 1);
                    FieldMeta fm = findFieldMeta(em, columnName);
                    Object columnValue = decryptIfMarked(resultSet.getObject(_iterator + 1), fm);
                    if (fm != null) {
                        BeanUtils.setProperty(bean, fm.getFieldName(), columnValue);
                    }
                }
                return bean;
            } catch (InstantiationException
                     | IllegalAccessException
                     | InvocationTargetException
                     | NoSuchMethodException e) {
                log.error(String.valueOf(e));
            }
        }
        return (T) mapRowAsMap(resultSet, resultSetMetaData, em);
    }

    /**
     * 无 Java 类映射（如 DB 在线源实体）或元数据缺失时，按元数据字段名（无元数据则按列名）退化为 Map 返回，
     * 保证查询数据不丢失。
     */
    private Map<String, Object> mapRowAsMap(ResultSet resultSet, ResultSetMetaData metaData, EntityMeta em) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            String columnName = metaData.getColumnName(index);
            FieldMeta fm = em == null ? null : findFieldMeta(em, columnName);
            String mapKey = fm != null ? fm.getFieldName() : columnName;
            map.put(mapKey, decryptIfMarked(resultSet.getObject(index), fm));
        }
        return map;
    }

    private static FieldMeta findFieldMeta(EntityMeta em, String columnName) {
        if (em.getFieldMetas() == null) {
            return null;
        }
        for (FieldMeta fm : em.getFieldMetas()) {
            if (columnName.equals(fm.getColumnName())) {
                return fm;
            }
        }
        return null;
    }

    /** 仅对元数据标记加密的列解密;未匹配到字段元数据的列原样返回 */
    private static Object decryptIfMarked(Object value, FieldMeta fm) {
        if (!(value instanceof String stringValue)) {
            return value;
        }
        boolean marked = fm != null && fm.getColumnMeta() != null && fm.getColumnMeta().isEncrypted();
        return marked ? EncryptUtils.decrypt(stringValue) : stringValue;
    }
}
