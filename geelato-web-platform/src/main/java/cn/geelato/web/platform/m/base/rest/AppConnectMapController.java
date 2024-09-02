package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.entity.AppConnectMap;
import cn.geelato.web.platform.m.base.service.AppConnectMapService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@ApiRestController("/app/connect")
@Slf4j
public class AppConnectMapController extends BaseController {
    private static final Map<String, List<String>> OPERATORMAP = new LinkedHashMap<>();
    private static final Class<AppConnectMap> CLAZZ = AppConnectMap.class;

    static {
        OPERATORMAP.put("contains", Arrays.asList("appName", "connectName"));
        OPERATORMAP.put("intervals", Arrays.asList("createAt", "updateAt"));
    }

    private final AppConnectMapService appConnectMapService;

    @Autowired
    public AppConnectMapController(AppConnectMapService appConnectMapService) {
        this.appConnectMapService = appConnectMapService;
    }

    @RequestMapping(value = "/pageQueryOf", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult pageQueryOf(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        try {
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(req);
            Map<String, Object> params = this.getQueryParameters(req);
            result = appConnectMapService.pageQueryModel("page_query_platform_app_r_connect", params, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return result;
    }
}
