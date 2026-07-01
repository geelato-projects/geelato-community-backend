package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.platform.run.monitor.schedule.ScheduledTaskMonitorRegistry;
import cn.geelato.web.platform.srv.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@DesignTimeApiRestController("/system/monitor/scheduled-tasks")
@Slf4j
public class ScheduledTaskMonitorController extends BaseController {
    private final ScheduledTaskMonitorRegistry registry;

    public ScheduledTaskMonitorController(ScheduledTaskMonitorRegistry registry) {
        this.registry = registry;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ApiResult<?> list() {
        try {
            if (!SecurityContext.isAdmin()) {
                return ApiResult.fail("无权限");
            }
            return ApiResult.success(registry.getSummary());
        } catch (Exception e) {
            log.error("query scheduled task monitor failed", e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/refresh", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> refresh() {
        try {
            if (!SecurityContext.isAdmin()) {
                return ApiResult.fail("无权限");
            }
            return ApiResult.success(registry.scanNow());
        } catch (Exception e) {
            log.error("refresh scheduled task monitor failed", e);
            return ApiResult.fail(e.getMessage());
        }
    }
}

