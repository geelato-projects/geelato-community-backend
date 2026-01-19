package cn.geelato.web.platform.run;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@ApiRestController("/run/log")
public class PlatformLogSearchController {
    @Autowired
    private PlatformLogSearchService logSearchService;

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public ApiResult<?> search(@RequestParam String tag) {
        return logSearchService.findFirstByLogTag(tag)
                .map(hit -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("file", hit.getFile().toString());
                    result.put("lineNumber", hit.getLineNumber());
                    result.put("lines", hit.getLines());
                    return ApiResult.success(result);
                })
                .orElseGet(() -> ApiResult.fail("未找到匹配日志"));
    }
}
