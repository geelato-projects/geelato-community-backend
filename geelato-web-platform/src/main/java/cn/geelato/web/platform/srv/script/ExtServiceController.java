package cn.geelato.web.platform.srv.script;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.graal.utils.GraalUtils;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionException;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionResult;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionService;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;

@ApiRestController("/ext")
@Slf4j
public class ExtServiceController extends BaseController {
    private final RuleService ruleService;
    private final ScriptExecutionService scriptExecutionService;

    @Autowired
    public ExtServiceController(RuleService ruleService, ScriptExecutionService scriptExecutionService) {
        this.ruleService = ruleService;
        this.scriptExecutionService = scriptExecutionService;
    }

    @IgnoreVerify
    @RequestMapping(value = "{outside_url}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    @SuppressWarnings("rawtypes")
    public Object exec(@PathVariable("outside_url") String outside_url) {
        String parameter = resolveBody(this.request);
        try {
            ScriptExecutionResult result = scriptExecutionService.executeExternalByOutsideUrl(outside_url, parameter);
            createApiLogByLevel(result.getApi().getLogLevel(), "info", result.getApi().getAppId(), result.getApi().getCode(), parameter, null, null, null, JSONObject.toJSONString(result.getResult()));
            if (result.getApi().getResponseFormat() != null && "custom".equalsIgnoreCase(result.getApi().getResponseFormat())) {
                return JSONObject.toJSONString(result.getResult());
            }
            return ApiResult.success(result.getResult());
        } catch (ScriptExecutionException ex) {
            if (ex.getApi() != null) {
                createApiLogByLevel(ex.getApi().getLogLevel(), "error", ex.getApi().getAppId(), ex.getApi().getCode(), parameter, null, null, null, ex.getMessage());
            }
            log.error("script error:{}", ex.getMessage(), ex);
            return ApiResult.fail(ex.getMessage());
        }
    }

    private void createApiLogByLevel(String level, String dg, String appId, String code, String rp, String rb, String rh, String rc, String resp) {
        if (Strings.isNotBlank(level) && level.contains("dg")) {
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
        StringBuilder gql = new StringBuilder();
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
