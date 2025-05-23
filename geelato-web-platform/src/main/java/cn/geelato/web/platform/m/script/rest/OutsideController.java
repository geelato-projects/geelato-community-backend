package cn.geelato.web.platform.m.script.rest;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.graal.utils.GraalUtils;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.script.entity.Api;
import cn.geelato.web.platform.m.script.service.ApiService;
import cn.geelato.web.platform.utils.GqlUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController("/ext")
@Slf4j
public class OutsideController extends BaseController {
    private final GraalManager graalManager = GraalManager.singleInstance();
    private final ApiService apiService;
    private final RuleService ruleService;

    @Autowired
    public OutsideController(ApiService apiService, RuleService ruleService) {
        this.apiService = apiService;
        this.ruleService = ruleService;
    }

    @IgnoreVerify
    @RequestMapping(value = "{outside_url}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    @SuppressWarnings("rawtypes")
    public Object exec(@PathVariable("outside_url") String outside_url) throws IOException {
        String parameter = GqlUtil.resolveGql(this.request);
        Api api = null;
        Map<String, Object> params = new HashMap<>();
        params.put("outsideUrl", "/" + outside_url);
        List<Api> apiList = apiService.queryModel(Api.class, params);
        if (apiList != null && !apiList.isEmpty()) {
            api = apiList.get(0);
        }
        if (api != null) {
            String scriptContent = getScriptContent(api.getReleaseContent());
            try {
                Context context = Context.newBuilder("js")
                        .allowHostAccess(HostAccess.ALL)
                        .allowIO(true)
                        .allowHostClassLookup(className -> true).build();
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
                Map result = context.eval(source).execute(JSON.parse(parameter)).as(Map.class);
                // 记录日志
                createApiLogByLevel(api.getLogLevel(), "info", api.getAppId(), api.getCode(), parameter, null, null, null, JSONObject.toJSONString(result.get("result")));
                // 返回结果
                if (api.getResponseFormat() != null && "custom".equalsIgnoreCase(api.getResponseFormat())) {
                    return JSONObject.toJSONString(result.get("result"));
                } else {
                    return ApiResult.success(result.get("result"));
                }
            } catch (Exception e) {
                createApiLogByLevel(api.getLogLevel(), "error", api.getAppId(), api.getCode(), parameter, null, null, null, e.getMessage());
                log.error("script error:{}", e.getMessage());
                return ApiResult.fail(e.getMessage());
            }
        } else {
            return ApiResult.fail("not found script");
        }
    }

    private void createApiLogByLevel(String level, String dg, String appId, String code, String rp, String rb, String rh, String rc, String resp) {
        if (Strings.isNotBlank(level) && level.indexOf("dg") != -1) {
            createApiLog(dg, appId, code, rp, rb, rh, rc, resp);
        }
    }

    /**
     * 创建API日志。
     *
     * @param dg    调试等级
     * @param appId 应用ID
     * @param code  状态码
     * @param rp    请求参数
     * @param rb    请求体
     * @param rh    请求头
     * @param rc    请求Cookie
     * @param resp  响应参数
     */
    private void createApiLog(String dg, String appId, String code, String rp, String rb, String rh, String rc, String resp) {
        GraalUtils.getCurrentTenantCode();
        StringBuffer gql = new StringBuffer();
        gql.append("{\"@biz\":\"0\",\"").append("platform_api_log").append("\":{");
        gql.append("\"").append("code").append("\":").append(JSON.toJSONString(code)).append(",");
        gql.append("\"").append("requestParams").append("\":").append(JSON.toJSONString(rp)).append(",");
        gql.append("\"").append("requestBody").append("\":").append(JSON.toJSONString(rb)).append(",");
        gql.append("\"").append("requestHeaders").append("\":").append(JSON.toJSONString(rh)).append(",");
        gql.append("\"").append("requestCookies").append("\":").append(JSON.toJSONString(rc)).append(",");
        gql.append("\"").append("responseParams").append("\":").append(JSON.toJSONString(resp)).append(",");
        gql.append("\"").append("logLevel").append("\":").append(JSON.toJSONString(dg)).append(",");
        gql.append("\"").append("appId").append("\":").append(JSON.toJSONString(appId)).append(",");
        gql.deleteCharAt(gql.length() - 1);
        gql.append("}}");
        ruleService.save("0", gql.toString());
    }

    private String getScriptContent(String customContent) {
        String scriptTemplate = scriptTemplate();
        return scriptTemplate.replace("#scriptContent#", customContent);
    }

    private String scriptTemplate() {
        return """
                (function(parameter){
                \t var ctx={};
                \t ctx.parameter=parameter;
                \t ctx.result=#scriptContent# ();
                \t return ctx;\t
                })""";
    }
}
