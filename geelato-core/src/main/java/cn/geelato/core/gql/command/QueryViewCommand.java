package cn.geelato.core.gql.command;

import cn.geelato.core.gql.filter.FilterGroup;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

@Setter
@Getter
public class QueryViewCommand extends QueryCommand {

    private String viewName;
    public QueryViewCommand() {
        setCommandType(CommandType.Query);
    }
}
