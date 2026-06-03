package cn.geelato.orm.query;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class JoinClause {
    private String entityName;
    private String alias;
    private JoinType joinType;
    private List<JoinCondition> conditions = new ArrayList<>();
}
