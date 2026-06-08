package cn.geelato.web.platform.srv.script;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.JsonUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.graal.GraalContext;
import cn.geelato.web.platform.graal.GraalExecutor;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.Api;
import cn.geelato.web.platform.srv.script.service.ApiService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
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
        String parameter = resolveBody(this.request);
        String scriptContent = getScriptContent(scriptId);
        Object parsedParameter = JsonUtils.safeParse(parameter);
        try (Context context = GraalContext.getContext()) {
            Map<String, Object> graalServiceMap = graalManager.getGraalServiceMap();
            Map<String, Object> graalVariableMap = graalManager.getGraalVariableMap();
            Map<String, Object> globalGraalVariableMap = new HashMap<>(graalManager.getGlobalGraalVariableMap());
            globalGraalVariableMap.remove("ctx");
            if (!StringUtils.isEmpty(parameter)) {
                Map<String, Object> ctxMap = Map.of("parameter", parsedParameter);
                globalGraalVariableMap.put("ctx", ctxMap);
            }
            context.getBindings(GraalUse.Language_JS).putMember(GraalUse.GLOBAL_OBJECT, globalGraalVariableMap);
            for (Map.Entry entry : graalServiceMap.entrySet()) {
                context.getBindings(GraalUse.Language_JS).putMember(entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry entry : graalVariableMap.entrySet()) {
                context.getBindings(GraalUse.Language_JS).putMember(entry.getKey().toString(), entry.getValue());
            }

            context.getBindings(GraalUse.Language_JS).putMember(GraalUse.GLOBAL_EXECUTOR, new GraalExecutor(apiService));

            Source source = Source.newBuilder(GraalUse.Language_JS, scriptContent, GraalUse.BASE_SCRIPT_JS_FILE).build();
            Map result = context.eval(source).execute(parsedParameter).as(Map.class);
            return ApiResult.success(result.get("result"));
        }

    }

    private String getScriptContent(String scriptId) {
        return GraalUse.BASE_SCRIPT_CONTENT.replace(GraalUse.CUSTOM_CONTENT_TAG, customContent(scriptId));
    }

    private String customContent(String id) {
        Api api = apiService.getModel(Api.class, id);
        return api.getReleaseContent();
    }

    private String resolveBody(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        String str;
        try (BufferedReader br = request.getReader()) {
            if (br != null) {
                while ((str = br.readLine()) != null) {
                    stringBuilder.append(str);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return stringBuilder.toString();
    }
}
