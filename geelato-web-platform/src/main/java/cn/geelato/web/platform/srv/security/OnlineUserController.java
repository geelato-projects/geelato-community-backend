package cn.geelato.web.platform.srv.security;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.service.RedisOnlineUserTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@ApiRestController("/online")
@Slf4j
public class OnlineUserController extends BaseController {
    private final RedisOnlineUserTracker onlineUserTracker;

    @Autowired
    public OnlineUserController(RedisOnlineUserTracker onlineUserTracker) {
        this.onlineUserTracker = onlineUserTracker;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiResult<?> list(@RequestParam(value = "windowMinutes", required = false) Integer windowMinutes,
                             @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            if (!SecurityContext.isAdmin()) {
                return ApiResult.fail("无权限");
            }
            return ApiResult.success(onlineUserTracker.listOnline(windowMinutes, limit));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
