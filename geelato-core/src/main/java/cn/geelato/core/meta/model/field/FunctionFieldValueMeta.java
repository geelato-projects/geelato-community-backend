package cn.geelato.core.meta.model.field;

import cn.geelato.core.meta.EntityManager;
import cn.geelato.core.meta.MetaManager;
import lombok.Getter;
import lombok.Setter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Setter
@Getter
public class FunctionFieldValueMeta extends FieldValueMeta {

    MetaManager metaManager=MetaManager.singleInstance();
    private String mysql_function;
    public FunctionFieldValueMeta(FieldMeta fieldMeta, String functionExpression){
        this.mysql_function=resolveFunctionExpression(functionExpression);
        this.fieldMeta = fieldMeta;
    }

    private String resolveFunctionExpression(String functionExpression) {
        Pattern pattern = Pattern.compile("\\((.*?)\\)");
        Matcher matcher = pattern.matcher(functionExpression);
        if (matcher.find()) {
            String paramPartial = matcher.group(1);
            String[] ps = paramPartial.split(",");
            String param1 = ps[0];
            String param2 = ps[1];
            if (param1.startsWith("$")) {
                param1 = resolveSpecialParam(param1);
            } else {
                param1 = ps[1];
            }
            return String.format("gfn_increment(%s,%s)", param1, param2);
        } else {
            return null;
        }
    }

    private String resolveSpecialParam(String param) {
        String[] paramsPartial=param.split("//.");
        return metaManager.getByEntityName(paramsPartial[1]).getColumnName(paramsPartial[2]);
    }
}
