package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.TypeConverter;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.SaveCommand;
import cn.geelato.core.meta.model.entity.EntityMeta;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;

/**
 * @author geemeta
 */
@Component
@Slf4j
public class MetaUpdateSqlProvider extends MetaBaseSqlProvider<SaveCommand> {

    @Override
    protected Object[] buildParams(SaveCommand command) {
        Assert.notNull(command.getValueMap(), "必须有指的更新字段。");
        EntityMeta em = getEntityMeta(command);
        ArrayList objectList = new ArrayList();
        // 值部分
        command.getValueMap().forEach((key, value) -> {
            if (!em.isIgnoreUpdateField(key)) {
                // 1、先加值部分
                objectList.add(value);
            }
        });

        //条件部分
        Object[] whereObjects = buildWhereParams(command);

        //2、再加条件部分
        for (Object object : whereObjects) {
            objectList.add(object);
        }
        return objectList.toArray();
    }

    @Override
    protected int[] buildTypes(SaveCommand command) {
        Assert.notNull(command.getValueMap(), "必须有指的更新字段。");
        EntityMeta em = getEntityMeta(command);
        // 条件部分
        int[] whereTypes = buildWhereTypes(command);

        ArrayList<Integer> typeList = new ArrayList();
        // 值部分
        command.getValueMap().forEach((key, value) -> {
            if (!em.isIgnoreUpdateField(key)) {
                // 1、先加值部分
                typeList.add(TypeConverter.toSqlType(em.getFieldMeta(key).getColumn().getDataType()));
            }
        });

        // 2、再加条件部分
        for (int type : whereTypes) {
            typeList.add(type);
        }

        int[] types = new int[typeList.size()];
        int i = 0;
        for (int type : typeList) {
            types[i] = type;
            i++;
        }
        return types;
    }

    /**
     * UPDATE 表名称 SET 列名称 = 新值 WHERE 列名称 = 某值
     * UPDATE Person SET Address = 'Zhongshan 23', City = 'Nanjing' WHERE LastName = 'Wilson'
     *
     * @param command 保存命令
     * @return 构建的保存语句
     */
    @Override
    protected String buildOneSql(SaveCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta em = getEntityMeta(command);
        sb.append("update ");
        sb.append(em.getTableName());
        sb.append(" set ");
        buildFields(sb, em, command.getFields());
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && fg.getFilters().size() > 0) {
            sb.append(" where ");
            buildConditions(sb, em, fg.getFilters(), fg.getLogic());
        }
        return sb.toString();
    }

    protected void buildFields(StringBuilder sb, EntityMeta em, String[] fields) {
        //重命名查询的结果列表为实体字段名
        for (String fieldName : fields) {
            if (em.isIgnoreUpdateField(fieldName)) {
                continue;
            }
            tryAppendKeywords(sb, em.getColumnName(fieldName));
            sb.append("=?,");
        }
        sb.deleteCharAt(sb.length() - 1);
    }
}
