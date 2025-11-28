package cn.geelato.web.platform.graal;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Api;
import cn.geelato.utils.JsonUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.script.GraalUse;
import cn.geelato.web.platform.srv.script.service.ApiService;
import com.alibaba.fastjson.JSONObject;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.Map;

public class GraalExecutor {
    private final ApiService apiService;
    private final Context context;
    public GraalExecutor(ApiService apiService) {
        this.context =Context.newBuilder(GraalUse.Language_JS)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build();
        this.apiService = apiService;
    }
    public Object exec(String code, Object parameter) {
        String codeContent=getScriptContent(code);
        if(StringUtils.isEmpty(codeContent)){
            return null;
        }
        try {
            Map<String, Object> graalServiceMap =  GraalManager.singleInstance().getGraalServiceMap();
            Map<String, Object> graalVariableMap =  GraalManager.singleInstance().getGraalVariableMap();
            Map<String, Object> globalGraalVariableMap =  GraalManager.singleInstance().getGlobalGraalVariableMap();
            if(parameter!=null){
                Map<String, Object> ctxMap = Map.of("parameter", JsonUtils.safeParse(parameter.toString()));
                globalGraalVariableMap.put("ctx",ctxMap);
            }
            context.getBindings(GraalUse.Language_JS).putMember(GraalUse.GLOBAL_OBJECT, globalGraalVariableMap);
            for (Map.Entry entry : graalServiceMap.entrySet()) {
                context.getBindings(GraalUse.Language_JS).putMember(entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry entry : graalVariableMap.entrySet()) {
                context.getBindings(GraalUse.Language_JS).putMember(entry.getKey().toString(), entry.getValue());
            }

            context.getBindings(GraalUse.Language_JS).putMember(GraalUse.GLOBAL_EXECUTOR, new GraalExecutor(apiService));

            Source source = Source.newBuilder(GraalUse.Language_JS, codeContent, GraalUse.BASE_SCRIPT_JS_FILE).build();
            Map result = context.eval(source).execute().as(Map.class);
            return result.get("result");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getScriptContent(String scriptCode) {
        Api api = apiService.queryModel(Api.class, Map.of("code",scriptCode)).get(0);
        return GraalUse.BASE_SCRIPT_CONTENT.replace(GraalUse.CUSTOM_CONTENT_TAG, api.getReleaseContent());
    }
}
