package cn.geelato.core.meta.model.field;

import cn.geelato.core.meta.MetaManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum GFunction implements FunctionResolver {


    increment {
        @Override
        public String resolve(String functionExpression) {
            Pattern pattern = Pattern.compile("\\((.*?)\\)");
            Matcher matcher = pattern.matcher(functionExpression);
            if (matcher.find()) {
                String paramPartial = matcher.group(1);
                String[] ps = paramPartial.split(",");
                String param1 = ps[0];
                String param2 = ps[1];
                if (param1.startsWith("$")) {
                    param1 = resolveSpecialParam(param1);
                }
                if (param2.startsWith("$")) {
                    param2 = resolveSpecialParam(param2);
                }
                return String.format("gfn_increment(%s,%s)", param1, param2);
            } else {
                return null;
            }
        }

        private String resolveSpecialParam(String param) {
            String[] paramsPartial = param.split("\\.");
            return MetaManager.singleInstance().getByEntityName(paramsPartial[0].replace("$", ""))
                    .getColumnName(paramsPartial[1]);
        }

    },
    findinset {
        @Override
        public String resolve(String functionExpression) {
            Pattern pattern = Pattern.compile("\\((.*?)\\)");
            Matcher matcher = pattern.matcher(functionExpression);
            if (matcher.find()) {
                String paramPartial = matcher.group(1);
                String[] ps = paramPartial.split(",");
                String param1 = ps[0];
                String param2 = ps[1];
                if (param1.startsWith("$")) {
                    param1 = resolveSpecialParam(param1);
                }
                if (param2.startsWith("$")) {
                    param2 = resolveSpecialParam(param2);
                }
                return String.format("gfn_findinset(%s,%s)", param1, param2);
            } else {
                return null;
            }
        }

        private String resolveSpecialParam(String param) {
            String[] paramsPartial = param.split("\\.");
            return MetaManager.singleInstance().getByEntityName(paramsPartial[0].replace("$", ""))
                    .getColumnName(paramsPartial[1]);
        }
    };
    private static final GFunction[] VALUES = values();

    public static GFunction lookUp(String expression) {
        String functionName = null;
        Pattern pattern = Pattern.compile("(.*)\\(.*\\)");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            functionName = matcher.group(1);
        }
        for (GFunction value : VALUES) {
            if (value.name().equals(functionName)) {
                return value;
            }
        }
        return null;
    }
}
