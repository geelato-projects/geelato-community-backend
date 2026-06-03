package cn.geelato.orm.query;

import java.util.ArrayList;
import java.util.List;

public class JoinOn {
    private final List<JoinCondition> conditions = new ArrayList<>();

    public JoinOn eqField(String leftField, String rightField) {
        JoinCondition condition = new JoinCondition();
        condition.setLeftField(leftField);
        condition.setOperator("=");
        condition.setRightField(rightField);
        conditions.add(condition);
        return this;
    }

    public JoinOn raw(String expression) {
        JoinCondition condition = new JoinCondition();
        condition.setRawExpression(expression);
        conditions.add(condition);
        return this;
    }

    public List<JoinCondition> getConditions() {
        return conditions;
    }
}
