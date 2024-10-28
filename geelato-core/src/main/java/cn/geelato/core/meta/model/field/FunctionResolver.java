package cn.geelato.core.meta.model.field;

public interface FunctionResolver {
    FunctionFieldValue resolve(String functionExpression);
}
