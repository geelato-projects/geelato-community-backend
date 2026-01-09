package cn.geelato.core.mql.command;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class QueryViewCommand extends QueryCommand {

    private String viewName;
    public QueryViewCommand() {
        setCommandType(CommandType.Query);
    }
}
