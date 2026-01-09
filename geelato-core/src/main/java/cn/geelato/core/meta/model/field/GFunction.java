package cn.geelato.core.meta.model.field;

import cn.geelato.core.meta.MetaManager;

import java.util.ArrayList;
import java.util.List;
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
    },
    fuzzymatch {
        @Override
        public String resolve(String functionExpression) {
            Pattern pattern = Pattern.compile("\\((.*?)\\)");
            Matcher matcher = pattern.matcher(functionExpression);
            if (matcher.find()) {
                String paramPartial = matcher.group(1);
                String[] ps = splitParamsRespectQuotes(paramPartial);
                if (ps.length < 2) {
                    return null;
                }
                String param1 = ps[0].trim();
                String param2 = ps[1].trim();
                if (param1.startsWith("$")) {
                    param1 = resolveSpecialParam(param1);
                }
                if (param2.startsWith("$")) {
                    param2 = resolveSpecialParam(param2);
                } else if (param2.startsWith("'") && param2.endsWith("'")) {
                    // 保留单引号包裹的字符串作为第二参数
                    // 去除内部多余空格，仅保留原始字面量内容
                    String inner = param2.substring(1, param2.length() - 1).trim();
                    param2 = "'" + inner + "'";
                }
                return String.format("gfn_fuzzymatch(%s,%s)", param1, param2);
            } else {
                return null;
            }
        }

        private String resolveSpecialParam(String param) {
            String[] paramsPartial = param.split("\\.");
            return MetaManager.singleInstance().getByEntityName(paramsPartial[0].replace("$", ""))
                    .getColumnName(paramsPartial[1]);
        }

        private String[] splitParamsRespectQuotes(String s) {
            List<String> parts = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inSingle = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\'') {
                    inSingle = !inSingle;
                    cur.append(c);
                    continue;
                }
                if (c == ',' && !inSingle) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
            if (!cur.isEmpty()) {
                parts.add(cur.toString());
            }
            return parts.toArray(new String[0]);
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
