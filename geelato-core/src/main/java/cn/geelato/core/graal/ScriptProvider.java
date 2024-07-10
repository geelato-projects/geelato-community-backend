package cn.geelato.core.graal;

import com.alibaba.fastjson2.JSON;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

public class ScriptProvider {
    public static void execVoid(String scriptContent,Object scriptParameter){
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build()) {
            Value jsFunction = context.eval("js",scriptContent);
            jsFunction.execute(scriptParameter);
        }
    }
    public static void execSetCtx(String scriptContent,Object ctx){
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build()) {
            context.getBindings("js").putMember("ctx",ctx);
            Value jsFunction = context.eval("js",scriptContent);
            jsFunction.execute();
        }
    }

    private static void execSetJavaDaoInstance(String scriptContent, TestOp testOp) {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build()) {
            context.getBindings("js").putMember("dao",testOp);
            Value jsFunction = context.eval("js",scriptContent);
            jsFunction.execute();
        }
    }
    public static Object execHasOutput(String scriptContent,Object scriptParameter){
       Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build();
           return  context.eval("js",scriptContent).execute(scriptParameter);
    }

    public static Object execHasOutput(String scriptContent,Object scriptParameter,Class T){
        Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .build();
            Value jsFunction = context.eval("js",scriptContent);
            return jsFunction.execute(scriptParameter).as(T);
    }

    public static void main(String[] args) {
        execVoid();  //执行空返回方法,success
        execStringParameter(); //执行String参数输入方法,success
        execMapParameter(); //执行Map参数输入方法,success
        execObjectParameter(); //执行对象参数输入方法,success
        execOutStringResult();  //执行String结果输出方法,success
        execOutMapResult(); //执行Map结果输出方法,success
        execOutObjectResult(); //执行Object结果输出方法,需输出Map,再转对象，success
        execOutObjectResultFromInputObject(); //执行Object结果输出方法，对象为输入，success
        execSetGlobalObject(); // 设置全局对象方法,success

        execJavaMethod();

    }

    private static void execJavaMethod() {
        String scriptContent="(function(){" +
                "dao.testMethod();" +
                "});";
        execSetJavaDaoInstance(scriptContent,new TestOp());
    }



    private static void execOutObjectResultFromInputObject() {
        GraaljsTestObj obj=new GraaljsTestObj();
        obj.setProp1("prop1Value");
        obj.setProp2("prop2Value");
        String scriptContent="(function(parameter){" +
                "return parameter;" +
                "});";
        GraaljsTestObj result= (GraaljsTestObj) execHasOutput(scriptContent,obj,GraaljsTestObj.class);
        if(result!=null){
            System.out.println(result.getProp1());
            System.out.println(result.getProp2());
        }
    }

    private static void execSetGlobalObject() {
        String scriptContent="(function(){" +
                "console.log(ctx.key1);" +
                "console.log(ctx.key2);" +
                "});";
        Map<String,Object> ctx=new HashMap<>();
        ctx.put("key1","value1");
        ctx.put("key2","value2");
        execSetCtx(scriptContent,ctx);
    }

    private static void execOutObjectResult() {
        String scriptContent="(function(){" +
                "var result=new Object();" +
                "result.prop1='prop1Value';" +
                "result.prop2='prop2Value';" +
                "return result;" +
                "});";
        Map result= (Map) execHasOutput(scriptContent,null,Map.class);
        if(result!=null){
            String s = JSON.toJSONString(result);
            GraaljsTestObj obj = JSON.parseObject(s, GraaljsTestObj.class);
            System.out.println(obj.getProp1());
            System.out.println(obj.getProp2());
        }
    }

    private static void execOutMapResult() {
        String scriptContent="(function(parameter){" +
                "var result={};" +
                "result.key1='value1';" +
                "result.key2='value2';" +
                "return result;" +
                "});";
        Map rstMap= (Map) execHasOutput(scriptContent,null,Map.class);
        if(rstMap!=null){
            System.out.println(rstMap.get("key1"));
            System.out.println(rstMap.get("key2"));
        }
    }

    private static void execOutStringResult() {
        String scriptContent="(function(parameter){" +
                "var result='graaljs_result';" +
                "return result;" +
                "});";
        Value result= (Value) execHasOutput(scriptContent,null);
        if(result.isString()){
            System.out.print(result.asString());
        }
    }

    private static void execObjectParameter() {
        GraaljsTestObj obj=new GraaljsTestObj();
        obj.setProp1("prop1Value");
        obj.setProp2("prop2Value");
        String scriptContent="(function(parameter){" +
                "console.log(JSON.stringify(parameter));" +
                "console.log(parameter.getProp1());" +
                "console.log(parameter.getProp2());" +
                "});";
        execVoid(scriptContent,obj);
    }

    private static void execMapParameter() {
        String scriptContent="(function(parameter){" +
                "var k1=parameter.key1;" +
                "var k2=parameter.key2;" +
                "console.log(k1);" +
                "console.log(k2);" +
                "});";
        Map<String,Object> parameter=new HashMap<>();
        parameter.put("key1","value1");
        parameter.put("key2","value2");
        execVoid(scriptContent,parameter);
    }


    private static void execStringParameter() {
        String scriptContent="(function(parameter){console.log(parameter)});";
        String parameter="graaljs_parameter";
        execVoid(scriptContent,parameter);
    }


    public static void execVoid(){
        String scriptContent="(function(){console.log('graaljs')});";
        execVoid(scriptContent,null);
    }



}
