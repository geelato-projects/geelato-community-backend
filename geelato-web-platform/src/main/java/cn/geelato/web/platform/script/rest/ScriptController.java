package cn.geelato.web.platform.script.rest;

import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.script.service.ApiService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.core.graal.GraalManager;
import cn.geelato.web.platform.graal.GraalContext;

import cn.geelato.web.platform.script.entty.Api;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping(value = "/api")
public class ScriptController extends BaseController {
    private GraalManager graalManager= GraalManager.singleInstance();

    @Resource
    private ApiService apiService;
    @RequestMapping(value = "/exec/{scriptId}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult<GraalContext> exec(@PathVariable("scriptId") String scriptId, HttpServletRequest request){
        String parameter=getBody(request);
        String scriptContent=getScriptContent(scriptId);
        Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build();
        Map<String,Object> graalServiceMap= graalManager.getGraalServiceMap();
        Map<String,Object> graalVariableMap= graalManager.getGraalVariableMap();
        Map<String,Object> globalGraalVariableMap= graalManager.getGlobalGraalVariableMap();
        context.getBindings("js").putMember("$gl", globalGraalVariableMap);
        for(Map.Entry entry : graalServiceMap.entrySet()){
            context.getBindings("js").putMember(entry.getKey().toString(), entry.getValue());
        }
        for(Map.Entry entry : graalVariableMap.entrySet()) {
            context.getBindings("js").putMember(entry.getKey().toString(), entry.getValue());
        }
        Map result = context.eval("js",scriptContent).execute(parameter).as(Map.class);
        return new ApiResult<>(new GraalContext(result.get("result"))).success();
    }

    private String getScriptContent(String scriptId) {
        String scriptTemplate=scriptTemplate();
        return scriptTemplate.replace("#scriptContent#",customContent(scriptId));
    }

    private String customContent(String id) {
        Api api= apiService.getModel(Api.class,id);
        return api.getRelease_content();
//        return "var result=$gl.dao.queryForMapList(gql,false);context.result=result;" +
//                "return result;";
    }

    private String scriptTemplate() {
        return "(function(parameter){\n" +
                "\t var context={};\n" +
                "\t context.parameter=parameter;\n" +
                "\t context.result=null;\n" +
//                "\t var $gl={};\n" +
//                "\t $gl.dao=GqlService;\n" +
//                "\t $gl.json=JsonService;\n" +
//                "\t $gl.user=userVariable;\n" +
//                "\t $gl.tenant=tenantVariable;\n" +
                "\t #scriptContent# \n" +
                "\t return context;\t\n" +
                "})";
    }

    private String getBody(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader br = null;
        try {
            br = request.getReader();
        } catch (IOException e) {
        }
        String str;
        try {
            while ((str = br.readLine()) != null) {
                stringBuilder.append(str);
            }
        } catch (IOException e) {
        }
        return stringBuilder.toString();
    }

}
