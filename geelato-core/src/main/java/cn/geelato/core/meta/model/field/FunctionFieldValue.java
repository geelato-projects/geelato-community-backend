package cn.geelato.core.meta.model.field;

import cn.geelato.core.meta.MetaManager;
import lombok.Getter;
import lombok.Setter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Setter
@Getter
public class FunctionFieldValue extends FieldValue {
    private FunctionFieldValue functionFieldValue;
    private String mysqlFunction;
    public FunctionFieldValue(FieldMeta fieldMeta, String functionExpression){
        GFunction gFunction=GFunction.lookUp(functionExpression);
        this.mysqlFunction=gFunction.resolve(functionExpression);
        this.fieldMeta = fieldMeta;
    }
    public FunctionFieldValue(String functionExpression){
        GFunction gFunction=GFunction.lookUp(functionExpression);
        this.mysqlFunction=gFunction.resolve(functionExpression);
    }
}
