package cn.geelato.web.platform.srv.script;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionException;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionResult;
import cn.geelato.web.platform.srv.script.service.ScriptExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;


@ApiRestController("/service")
public class ServiceController extends BaseController {
    private final ScriptExecutionService scriptExecutionService;

    public ServiceController(ScriptExecutionService scriptExecutionService) {
        this.scriptExecutionService = scriptExecutionService;
    }

    @RequestMapping(value = "/exec/{scriptId}", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult<?> exec(@PathVariable("scriptId") String scriptId) throws IOException {
        String parameter = resolveBody(this.request);
        try {
            ScriptExecutionResult result = scriptExecutionService.executeInternalById(scriptId, parameter);
            return ApiResult.success(result.getResult());
        } catch (ScriptExecutionException ex) {
            return ApiResult.fail(ex.getMessage());
        }
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
