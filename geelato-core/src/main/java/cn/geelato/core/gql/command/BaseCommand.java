package cn.geelato.core.gql.command;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author geemeta
 */
@Setter
@Getter
@SuppressWarnings("rawtypes")
public class BaseCommand<E extends BaseCommand> {
    /**
     * TODO 客户端生成的唯一标识，用于缓存
     */
    private String cacheKey;

    /**
     * -- GETTER --
     *
     */
    protected BaseCommand<E> parentCommand;

    private Boolean execution;

    protected CommandType commandType;
    // 命令对应实体名称
    protected String entityName;
    // 指定字段
    protected String[] fields;
    // 忽略字段
    protected String[] ignoreFields;
    // 指定条件
    protected FilterGroup where;
    //指定原始where语句
    protected String originalWhere;
    // 指定条件
    protected StringBuilder from = new StringBuilder();
    // 子命令
    protected List<E> commands=new ArrayList<>();;

    /**
     * @return 获取子命令，若不存在，则创建一个空的命令列表
     */

    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    public BaseCommand appendFrom(String from) {
        this.from.append(from);
        return this;
    }

    public BaseCommand appendFrom(String tablaName, String alias) {
        this.from.append(tablaName);
        if(!StringUtils.isEmpty(alias)) {
            this.from.append(" ").append(alias);
        }
        return this;
    }

    /**
     * from中是否已join该表
     *
     */
    public boolean hasNotJoin(String alias) {
        return this.from.indexOf(alias) == -1;
    }


}
