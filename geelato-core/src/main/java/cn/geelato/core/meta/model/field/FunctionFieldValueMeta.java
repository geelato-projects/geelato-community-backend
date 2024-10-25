package cn.geelato.core.meta.model.field;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FunctionFieldValueMeta extends FieldValueMeta {

    private String mysql_function;
    public FunctionFieldValueMeta(FieldMeta fieldMeta, String functionExpression){
        this.mysql_function=resolveFunctionExpression(functionExpression);
        this.fieldMeta = fieldMeta;
    }

    private String resolveFunctionExpression(String functionExpression) {
        return "increment";
    }


}
