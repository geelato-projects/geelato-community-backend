package cn.geelato.core.sql.provider;

import cn.geelato.core.mql.TypeConverter;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.meta.DeleteMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;

/**
 * @author geemeta
 */
@Component
@Slf4j
public class MetaDeleteSqlProvider extends MetaBaseSqlProvider<DeleteCommand> {

    @Override
    protected Object[] buildParams(DeleteCommand command) {
        EntityMeta em = getEntityMeta(command);
        ArrayList objectList = new ArrayList();
        // 值部分（仅逻辑删除）
        if (isLogicDelete(command)) {
            command.getValueMap().forEach((key, value) -> {
                if (!em.isIgnoreUpdateField(key)) {
                    // 1、先加值部分
                    objectList.add(value);
                }
            });
        }
        // 条件部分
        Object[] whereObjects = buildWhereParams(command);
        // 2、再加条件部分
        Collections.addAll(objectList, whereObjects);
        return objectList.toArray();
    }

    @Override
    protected int[] buildTypes(DeleteCommand command) {
        EntityMeta em = getEntityMeta(command);
        // 条件部分
        int[] whereTypes = buildWhereTypes(command);

        ArrayList<Integer> typeList = new ArrayList<>();
        if (isLogicDelete(command)) {
            // 值部分
            command.getValueMap().forEach((key, value) -> {
                if (!em.isIgnoreUpdateField(key)) {
                    typeList.add(TypeConverter.toSqlType(em.getFieldMeta(key).getColumnMeta().getDataType()));
                }
            });
        }

        // 再加条件部分
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
     * 构建单条SQL语句，按 {@link DeleteCommand#getDeleteMode()} 生成逻辑删除（update）或物理删除（delete from）。
     *
     * @param command 包含删除操作的命令对象
     * @return 构建的SQL语句字符串
     */
    @Override
    protected String buildOneSql(DeleteCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta em = getEntityMeta(command);
        em.setTableAlias(null);
        if (isLogicDelete(command)) {
            sb.append("update   ");
            sb.append(em.getTableName());
            sb.append(" set  ");
            buildFields(sb, em, command.getFields());
            appendWhere(sb, command);
        } else {
            sb.append("delete from ");
            sb.append(em.getTableName());
            appendWhere(sb, command);
        }
        return sb.toString();
    }

    private void appendWhere(StringBuilder sb, DeleteCommand command) {
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, getEntityMeta(command), fg.getFilters(), fg.getLogic());
        }
    }

    /**
     * 逻辑删除判定：deleteMode 未显式设置属调用链缺陷，直接失败；逻辑删除必须有可置字段。
     */
    private boolean isLogicDelete(DeleteCommand command) {
        DeleteMode mode = command.getDeleteMode();
        Assert.notNull(mode, "DeleteCommand.deleteMode 未设置，删除命令必须经 DeleteCommands.resolve 解析删除模式。");
        if (mode == DeleteMode.LOGIC) {
            Assert.notEmpty(command.getValueMap(), "逻辑删除必须有更新字段（delStatus 等），请经 DeleteCommands.fillLogicDeleteValues 填充。");
            return true;
        }
        return false;
    }

    protected void buildFields(StringBuilder sb, EntityMeta em, String[] fields) {
        // 重命名查询的结果列表为实体字段名
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
