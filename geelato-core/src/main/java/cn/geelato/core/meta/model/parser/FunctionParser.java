package cn.geelato.core.meta.model.parser;

public class FunctionParser {
    public static String reconstruct(String originExpression, String entityName) {
        return originExpression.replace("self",entityName);
    }


    public static boolean isFunction(String value) {
        return value.startsWith("increment");
    }
}
