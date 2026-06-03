package cn.geelato.core.mql.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryJoinCondition {
    private String leftField;
    private String operator;
    private String rightField;
    private String rawExpression;
}
