package cn.geelato.core.gql.execute;

import cn.geelato.core.gql.parser.BaseCommand;

import java.util.Map;

/**
 * @author geemeta
 */
public class BoundSql {
    private String name;
    private String sql;
    private Object[] params;
    private int[] types;
    private Map<String, BoundSql> boundSqlMap;
    private BaseCommand command;

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }

    public int[] getTypes() {
        return types;
    }

    public void setTypes(int[] types) {
        this.types = types;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, BoundSql> getBoundSqlMap() {
        return boundSqlMap;
    }

    public void setBoundSqlMap(Map<String, BoundSql> boundSqlMap) {
        this.boundSqlMap = boundSqlMap;
    }

    /**
     * @return 对应解析的command，通过该command，可以获取对应的实体信息
     */
    public BaseCommand getCommand() {
        return command;
    }

    public void setCommand(BaseCommand command) {
        this.command = command;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name:");
        sb.append(getName());
        sb.append(",sql:");
        sb.append(getSql());
        if (getParams() != null) {
            sb.append(",params:[");
            for (Object o : params) {
                sb.append(o);
                sb.append(",");
            }
            if (params.length > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("]");
        }
        return sb.toString();
    }
}
