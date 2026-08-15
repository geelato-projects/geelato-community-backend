package cn.geelato.web.platform.srv.notification;

import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.notification.dto.NotifyRequest;
import cn.geelato.web.platform.srv.notification.service.NotificationService;
import cn.geelato.web.platform.srv.notification.service.NotificationUserService;
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

    private final NotificationService notificationService;
    private final NotificationUserService notificationUserService;

    @Autowired
    public NotificationController(NotificationService notificationService,
                                  NotificationUserService notificationUserService) {
        this.notificationService = notificationService;
        this.notificationUserService = notificationUserService;
    }

    /**
     * 收件箱分页查询（当前用户）：收件人状态 JOIN 通知主体，返回含 title/content/actionUrl 的扁平行。
     * 可选过滤：readStatus（0未读/1已读）、archived（0/1）、bizType、keyword（标题/内容模糊）。
     */
    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery() {
        try {
            String userId = currentUserId();
            Map<String, Object> body = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(body);
            Integer readStatus = parseInteger(body.get("readStatus"));
            Integer archived = parseInteger(body.get("archived"));
            String bizType = body.get("bizType") != null && Strings.isNotBlank(String.valueOf(body.get("bizType")))
                    ? String.valueOf(body.get("bizType")).trim() : null;
            String keyword = body.get("keyword") != null && Strings.isNotBlank(String.valueOf(body.get("keyword")))
                    ? String.valueOf(body.get("keyword")).trim() : null;
            return notificationUserService.pageQueryInbox(userId, readStatus, archived, bizType, keyword, pageQueryRequest);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null || Strings.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
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
     * 删除当前用户收件箱中的一条通知（只影响本人，不影响其他收件人）。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/delete/{id}")
    public ApiResult<Boolean> delete(@PathVariable String id) {
        try {
            if (Strings.isBlank(id)) {
                return ApiResult.fail("通知ID不能为空");
            }
            String userId = currentUserId();
            boolean ok = notificationUserService.deleteInbox(id, userId);
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
