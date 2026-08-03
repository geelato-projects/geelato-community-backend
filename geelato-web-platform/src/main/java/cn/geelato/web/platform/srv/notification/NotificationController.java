package cn.geelato.web.platform.srv.notification;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.notification.dto.NotifyRequest;
import cn.geelato.web.platform.srv.notification.service.NotificationService;
import cn.geelato.web.platform.srv.notification.service.NotificationUserService;
import cn.geelato.meta.NotificationUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台通知中心 REST：收件箱查询、已读/星标/归档、未读数、主动发送、撤回。
 *
 * @author geelato
 */
@ApiRestController("/notification")
@Slf4j
public class NotificationController extends BaseController {

    private static final Class<NotificationUser> USER_CLAZZ = NotificationUser.class;

    private final NotificationService notificationService;
    private final NotificationUserService notificationUserService;

    @Autowired
    public NotificationController(NotificationService notificationService,
                                  NotificationUserService notificationUserService) {
        this.notificationService = notificationService;
        this.notificationUserService = notificationUserService;
    }

    /**
     * 收件箱分页查询（当前用户）。支持 read_status / archived 等过滤。
     */
    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            String userId = currentUserId();
            Map<String, Object> body = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(body);
            FilterGroup filterGroup = this.getFilterGroup(USER_CLAZZ, body, true);
            // 强制限定为当前用户，防越权
            filterGroup.addFilter("userId", userId);
            return notificationUserService.pageQueryInbox(USER_CLAZZ, filterGroup, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    /**
     * 当前用户未读数（铃铛角标）。
     */
    @GetMapping("/unread-count")
    public ApiResult<Long> unreadCount() {
        try {
            String userId = currentUserId();
            long count = notificationUserService.countUnread(userId);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 标记单条已读（带归属校验）。
     */
    @PostMapping("/read/{id}")
    public ApiResult<Boolean> markRead(@PathVariable String id) {
        try {
            if (Strings.isBlank(id)) {
                return ApiResult.fail("通知ID不能为空");
            }
            String userId = currentUserId();
            boolean ok = notificationUserService.markRead(id, userId);
            return ApiResult.success(ok);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 标记当前用户所有未读为已读。
     */
    @PostMapping("/read-all")
    public ApiResult<Integer> markAllRead() {
        try {
            String userId = currentUserId();
            int n = notificationUserService.markAllRead(userId);
            return ApiResult.success(n);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 星标/取消星标。
     */
    @PostMapping("/star/{id}")
    public ApiResult<Boolean> star(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return updateFlag(id, "starred", body);
    }

    /**
     * 归档/取消归档。
     */
    @PostMapping("/archive/{id}")
    public ApiResult<Boolean> archive(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return updateFlag(id, "archived", body);
    }

    private ApiResult<Boolean> updateFlag(String id, String field, Map<String, Object> body) {
        try {
            if (Strings.isBlank(id)) {
                return ApiResult.fail("通知ID不能为空");
            }
            int value = 1;
            if (body != null && body.get("value") != null) {
                value = Integer.parseInt(String.valueOf(body.get("value")));
            }
            String userId = currentUserId();
            boolean ok = notificationUserService.updateFlag(id, userId, field, value);
            return ApiResult.success(ok);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 主动发送通知（人工或系统）。走 dispatch 编排：写主体 + 按渠道写 outbox。
     */
    @PostMapping("/send")
    public ApiResult<String> send(@RequestBody NotifyRequest request) {
        try {
            String id = notificationService.dispatch(request);
            return ApiResult.success(id);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 撤回通知（逻辑删主体）。normalized 模型下，主体删除后全员收件箱关联失效。
     * 注：实际隐藏由主体 del_status 驱动（收件箱查询关联主体）。
     */
    @PostMapping("/recall/{id}")
    public ApiResult<Boolean> recall(@PathVariable String id) {
        try {
            if (Strings.isBlank(id)) {
                return ApiResult.fail("通知ID不能为空");
            }
            String operator = currentUserId();
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            java.util.List<cn.geelato.meta.Notification> list =
                    notificationUserService.queryModel(cn.geelato.meta.Notification.class, params);
            if (list == null || list.isEmpty()) {
                return ApiResult.fail("通知不存在");
            }
            cn.geelato.meta.Notification n = list.get(0);
            n.setDelStatus(1);
            n.setDeleteAt(new java.util.Date());
            n.setUpdateAt(new java.util.Date());
            n.setUpdater(operator);
            notificationUserService.updateModel(n);
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private String currentUserId() {
        String userId = SecurityContext.getCurrentUser().getUserId();
        if (Strings.isBlank(userId)) {
            throw new IllegalStateException("用户未登录");
        }
        return userId;
    }
}
