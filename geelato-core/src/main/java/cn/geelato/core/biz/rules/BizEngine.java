package cn.geelato.core.biz.rules;

import cn.geelato.core.script.sql.SqlScriptParser;

import javax.script.*;
import java.util.HashMap;

/**
 * @author geemeta
 *
 */
public class BizEngine {
    protected ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    protected HashMap<String, BizRule> bizRules = new HashMap<>();

    /**
     * biz
     */
    private String name;


    /**
     * @param ruleScript 规则脚本
     * @throws ScriptException 脚本解析出错
     */
    public void compile(String ruleScript) throws ScriptException {
        //TODO 从ruleScript中解析出fun
        String fun = "";

        BizRule bizRule = new BizRule();
        bizRule.setName(fun);
        bizRule.setScript(compile(fun, ruleScript));
        bizRules.put(fun, bizRule);
    }

    public Object invoke(String bizJsonText) {


        return null;
    }


    private CompiledScript compile(String funName, String jsFunText) throws ScriptException {
        ScriptEngine engine = scriptEngineManager.getEngineByName("javascript");

        return ((Compilable) engine).compile(jsFunText + ";" + funName + "(" + SqlScriptParser.VAL_NAME + ");");
    }

}
