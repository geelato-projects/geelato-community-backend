package cn.geelato.orm.query;


public class DefaultOperator implements Operator {

    @Override
    public QueryOperator query(String model) {
        return new QueryOperator(model);
    }
}
