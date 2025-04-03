package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.TypeConverter;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.meta.model.entity.EntityMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * @author geemeta
 */
@Component
@Slf4j
public class MetaInsertSqlProvider extends MetaBaseSqlProvider<SaveCommand> {

    @Override
    protected Object[] buildParams(SaveCommand command) {
        Assert.notNull(command.getValueMap(), "必须有指的插入字段。");

        Object[] objects = new Object[command.getValueMap().size()];
        int i = 0;
        for (Map.Entry<String, Object> entry : command.getValueMap().entrySet()) {
            objects[i] = entry.getValue();
            i++;
        }

        return objects;
    }

    @Override
    protected int[] buildTypes(SaveCommand command) {
        Assert.notNull(command.getValueMap(), "必须有指的插入字段。");
        EntityMeta em = getEntityMeta(command);
        int[] types = new int[command.getValueMap().size()];
        int i = 0;
        for (Map.Entry<String, Object> entry : command.getValueMap().entrySet()) {
            types[i] = TypeConverter.toSqlType(em.getFieldMeta(entry.getKey()).getColumnMeta().getDataType());
            i++;
        }
        return types;
    }

    /**
     * 构建一个SQL插入语句。
     * <p>
     * 根据传入的SaveCommand对象，构建一个用于插入数据的SQL语句。
     * 支持两种格式的SQL插入语句：
     * 1. INSERT INTO 表名称 VALUES (值1, 值2,....)
     * 2. INSERT INTO table_name (列1, 列2,...) VALUES (值1, 值2,....)
     *
     * @param command SaveCommand对象，包含要插入的数据信息
     * @return 构建好的SQL插入语句字符串
     */
    @Override
    protected String buildOneSql(SaveCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("insert into ");
        sb.append(md.getTableName());
        sb.append("(");
        buildFields(sb, md, command.getFields());
        sb.append(")");
        sb.append(" values ");
        sb.append("(");
        buildValues(sb, md, command.getFields());
        sb.append(")");
        return sb.toString();
    }

    protected void buildFields(StringBuilder sb, EntityMeta md, String[] fields) {
        for (String fieldName : fields) {
            sb.append(md.getColumnName(fieldName));
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
    }

    protected void buildValues(StringBuilder sb, EntityMeta md, String[] fields) {
        // 重命名查询的结果列表为实体字段名
        for (String fieldName : fields) {
            sb.append("?");
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
    }

}
