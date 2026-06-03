package cn.geelato.core.mql.command;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class QueryJoin {
    private String entityName;
    private String alias;
    private String joinType;
    private List<QueryJoinCondition> conditions = new ArrayList<>();
}
