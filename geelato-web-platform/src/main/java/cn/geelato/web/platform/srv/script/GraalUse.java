package cn.geelato.web.platform.srv.script;

public class GraalUse {
    public static final String Language_JS="js";

    public static final String GLOBAL_OBJECT="$gl";
    public static final String GLOBAL_EXECUTOR="$executor";

    public static final String BASE_SCRIPT_CONTENT="""
                (function(parameter){
                \t var ctx={};
                \t $gl.vars= $gl.vars || {};
                \t ctx.parameter=parameter;
                \t ctx.result=#scriptContent# ();
                \t return ctx;\t
                })""";

    public static final String CUSTOM_CONTENT_TAG="#scriptContent#";
    public static final String BASE_SCRIPT_JS_FILE="graal.mjs";
}
