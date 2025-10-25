package cn.geelato.web.platform.srv.script;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.Api;
import cn.geelato.web.platform.srv.script.service.ApiService;
import cn.geelato.web.platform.utils.GqlUtil;
import jakarta.annotation.Resource;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Map;


@ApiRestController("/service")
public class ServiceController extends BaseController {
    private final GraalManager graalManager = GraalManager.singleInstance();

    @Resource
    private ApiService apiService;

    @RequestMapping(value = "/exec/{scriptId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("rawtypes")
    public ApiResult<?> exec(@PathVariable("scriptId") String scriptId) throws IOException {
        String parameter = GqlUtil.resolveGql(this.request);
        String scriptContent = getScriptContent(scriptId);

        try (
                Context context = Context.newBuilder("js")
                        .allowHostAccess(HostAccess.ALL)
                        .allowIO(true)
                        .allowHostClassLookup(className -> true).build()) {
            Map<String, Object> graalServiceMap = graalManager.getGraalServiceMap();
            Map<String, Object> graalVariableMap = graalManager.getGraalVariableMap();
            Map<String, Object> globalGraalVariableMap = graalManager.getGlobalGraalVariableMap();
            context.getBindings("js").putMember("$gl", globalGraalVariableMap);
            for (Map.Entry entry : graalServiceMap.entrySet()) {
                context.getBindings("js").putMember(entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry entry : graalVariableMap.entrySet()) {
                context.getBindings("js").putMember(entry.getKey().toString(), entry.getValue());
            }
            Source source = Source.newBuilder("js", scriptContent, "graal.mjs").build();
            Map result = context.eval(source).execute(parameter).as(Map.class);
            return ApiResult.success(result.get("result"));
        }
    }

    private String getScriptContent(String scriptId) {
        String scriptTemplate = scriptTemplate();
        return scriptTemplate.replace("#scriptContent#", customContent(scriptId));
    }

    private String customContent(String id) {
        Api api = apiService.getModel(Api.class, id);
        return api.getReleaseContent();
    }

    private String scriptTemplate() {
//        String preImport="import {Foo} from 'graaljs/foo.mjs';\t" +
//                "import Base64 from 'graaljs/crypto-js/enc-base64.js';\t";
        return """
                (function(parameter){
                \t var sessionCtx={};
                \t sessionCtx.parameter=parameter;
                \t sessionCtx.result=#scriptContent# ();
                \t return sessionCtx;\t
                })""";
    }
}
