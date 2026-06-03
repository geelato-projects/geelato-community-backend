package cn.geelato.orm.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinCondition {
    private String leftField;
    private String operator;
    private String rightField;
    private String rawExpression;
}
