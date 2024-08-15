package cn.geelato.core.script.js;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.graalvm.polyglot.*;

/**
 * 先compile之后再调用{@link #execute}
 *
 * @author geemeta
 */
@Slf4j
public class JsProvider {

    private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    private final Map<String, JsFunctionInfo> jsFunctionInfoMap = new HashMap<>();
    // 格式例如：funName(a,b,c)
    private static final Pattern callScriptPattern = Pattern.compile("[\\S]*[\\s]?\\([\\S]*\\)");


    public JsProvider() {
    }

    /**
     * 编译多个js函数（function）片段
     *
     */
    public void compile(Map<String, String> jsFuncMap) throws ScriptException {
        if (jsFuncMap == null){ return;}
        for (Map.Entry<String, String> entry : jsFuncMap.entrySet()) {
            if (jsFunctionInfoMap.containsKey(entry.getKey())) {
                log.warn("collection exists key：{}", entry.getKey());
            } else {
                jsFunctionInfoMap.put(entry.getKey(), new JsFunctionInfo(entry.getKey(), entry.getValue(), ""));
            }
        }
    }

    /**
     * 在编译的同时，在function的结尾默认追加一条调用语句如：“;fun1(1,2)”
     *
     * @param functionName 函数名
     * @param scriptText   javascript function脚本片段，有具只有一个function,
     *                     格式如function fun1(a,b){return a+b}
     * @return
     * @throws ScriptException
     */
//    public CompiledScriptInfo compile(String functionName, String scriptText) throws ScriptException {
//        ScriptEngine jsEngine = engineManager.getEngineByName("graal.js");
//        Bindings bindings = jsEngine.getBindings(ScriptContext.ENGINE_SCOPE);
//        bindings.put("polyglot.js.allowHostAccess", true);
//        bindings.put("polyglot.js.allowHostClassLookup", (Predicate<String>) s -> true);
//        jsEngine.eval(scriptText);
//        Invocable jsFunction = (Invocable) jsEngine;
////        funcCall.
////
////        Context context = Context.newBuilder().allowAllAccess(true).build();
////        Value functionScript = context.eval("js", "("+scriptText+")");
//        // functionName + "(" + SqlScriptParser.VAL_NAME + ");"
////        String commandScriptString = matcherFnCallScript(scriptText);
////        Value commandScript = context.eval("js", "("+commandScriptString+")");
////        return new CompiledScriptInfo(functionName, functionScript, null);
//        return new CompiledScriptInfo(functionName, jsFunction, null);
//    }

    /**
     * @return 匹配脚本中的第一个function，取functionName(args..)，用于作调用function的执行脚本
     */
    public boolean contain(String functionName) {
        return jsFunctionInfoMap.containsKey(functionName);
    }

    /**
     * 调用预编译js脚本中的函数
     *
     * @param functionName 函数名称
     * @param paramMap     调用参数，若参数中需要用到实体时，需将实体转成JSONObject格式
     * @return 执行结果
     * @throws ScriptException 脚本错误
     */
    public Value execute(String functionName, Map<String, Object> paramMap) throws ScriptException, NoSuchMethodException {
        JsFunctionInfo jsFunctionInfo = jsFunctionInfoMap.get(functionName);
        Context context = Context.newBuilder("js").allowAllAccess(true).build();
        Value result = null;
        try {
            Value value = context.eval("js", "(" + jsFunctionInfo.getContent() + ")");
            if (value != null && value.canExecute()) {
                result = value.execute(paramMap);
            }
        } catch (Exception e) {
            log.error("执行脚本方法{}出错。", functionName, e);
        } finally {
            if (context != null) {
                context.close();
            }
        }
        return result;
    }

    public static Object executeExpression(String expression, Map<String, Object> paramMap) {
        Context context = Context.newBuilder("js").allowAllAccess(true).build();
        Object result = null;
        try {
            Value value = context.eval("js", "(function($){return " + expression + "})");
            if (value != null && value.canExecute()) {
                result = value.execute(paramMap).as(Object.class);
            }
        } catch (Exception e) {
            log.error("执行表达式{}出错。", expression, e);
        } finally {
            if (context != null) {
                context.close();
            }
        }
        return result;
    }

    /**
     * 编译的脚本信息
     */
    @Setter
    @Getter
    class JsFunctionInfo {
        private String functionName;
        private String description;
        private String content;

        public JsFunctionInfo(String functionName, String content, String description) {
            this.functionName = functionName;
            this.content = content;
            this.description = description;
        }

    }
}
