package cn.geelato.core.gql.command;

import cn.geelato.core.gql.filter.FilterGroup;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

/**
 * @author geemeta
 */
@Setter
@Getter
public class QueryTreeCommand extends QueryCommand {
    public QueryTreeCommand() {
        setCommandType(CommandType.Query);
    }

}
