package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.run.monitor.auxiliary.AuxiliarySuiteHealthPoller;
import cn.geelato.web.platform.srv.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController("/system/monitor/auxiliary-suites")
@Slf4j
public class AuxiliarySuiteHealthController extends BaseController {
    private final AuxiliarySuiteHealthPoller poller;

    public AuxiliarySuiteHealthController(AuxiliarySuiteHealthPoller poller) {
        this.poller = poller;
    }

    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public ApiResult<?> health() {
        try {
            if (!SecurityContext.isAdmin()) {
                return ApiResult.fail("无权限");
            }
            return ApiResult.success(poller.getLatestSummary());
        } catch (Exception e) {
            log.error("query auxiliary suite health failed", e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/refresh", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> refresh() {
        try {
            if (!SecurityContext.isAdmin()) {
                return ApiResult.fail("无权限");
            }
            return ApiResult.success(poller.refreshNow());
        } catch (Exception e) {
            log.error("refresh auxiliary suite health failed", e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
