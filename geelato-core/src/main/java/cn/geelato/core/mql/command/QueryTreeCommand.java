package cn.geelato.core.mql.command;

import lombok.Getter;
import lombok.Setter;

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
