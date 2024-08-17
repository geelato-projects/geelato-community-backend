package cn.geelato.core.gql.parser;

import cn.geelato.core.Ctx;
import cn.geelato.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author geemeta
 */
@SuppressWarnings("rawtypes")
public class BaseCommand<E extends BaseCommand> {
    /**
     * TODO 客户端生成的唯一标识，用于缓存
     */
    @Setter
    @Getter
    private String key;

    /**
     * -- GETTER --
     *
     * @return 如果是根命令，则返回null
     */
    @Setter
    @Getter
    protected BaseCommand<E> parentCommand;

    @Setter
    @Getter
    private Boolean execution;

    @Setter
    @Getter
    protected CommandType commandType;
    // 命令对应实体名称
    @Setter
    @Getter
    protected String entityName;
    // 指定字段
    @Setter
    @Getter
    protected String[] fields;
    // 指定条件
    @Setter
    @Getter
    protected FilterGroup where;
    //指定原始where语句
    @Setter
    @Getter
    protected String originalWhere;
    // 指定条件
    @Getter
    protected StringBuilder from = new StringBuilder();
    // 子命令
    @Setter
    protected List<E> commands;

    /**
     * @return 获取子命令，若不存在，则创建一个空的命令列表
     */
    public List<E> getCommands() {
        if (commands == null){ commands = new ArrayList<>();}
        return commands;
    }

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
