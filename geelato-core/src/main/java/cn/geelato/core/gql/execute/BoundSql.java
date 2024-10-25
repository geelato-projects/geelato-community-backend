package cn.geelato.core.gql.execute;

import cn.geelato.core.gql.parser.BaseCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author geemeta
 */
@Getter
@Setter
@SuppressWarnings("rawtypes")
public class BoundSql {
    private String name;
    private String sql;
    private Object[] params;
    private int[] types;
    private Map<String, BoundSql> boundSqlMap;
    private BaseCommand command;

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
