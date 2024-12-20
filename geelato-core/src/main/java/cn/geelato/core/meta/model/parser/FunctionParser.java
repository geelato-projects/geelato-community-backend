package cn.geelato.core.meta.model.parser;

import cn.geelato.utils.StringUtils;

public class FunctionParser {
    public static String reconstruct(String originExpression, String entityName) {
        return originExpression.replace("self",entityName);
    }


    public static boolean isFunction(String value) {
        if (StringUtils.isEmpty(value)) {
            return false;
        } else {
            return value.startsWith("increment")
                    || value.startsWith("gfn_increment")
                    || value.startsWith("findinset")
                    || value.startsWith("gfn_findinset");
        }
    }
}
