package cn.geelato.web.platform.srv.notice;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.Notice;
import cn.geelato.web.platform.srv.notice.service.NoticeOrmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@ApiRestController("/notice")
@Slf4j
public class NoticeController extends BaseController {

    private final NoticeOrmService noticeOrmService;

    @Autowired
    public NoticeController(NoticeOrmService noticeOrmService) {
        this.noticeOrmService = noticeOrmService;
    }

    /**
     * 查询通知列表
     */
    @GetMapping("/list")
    public ApiResult<List<Notice>> queryNoticeList(
            @RequestParam(required = false) String receiver,
            @RequestParam(required = false) String noticeTitle,
            @RequestParam(required = false) String status) {
        try {
            List<Notice> notices = noticeOrmService.queryNoticeList(receiver, noticeTitle, status);
            return ApiResult.success(notices);
        } catch (Exception e) {
            log.error("查询通知列表失败", e);
            return ApiResult.fail("查询通知列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询通知详情
     */
    @GetMapping("/{id}")
    public ApiResult<Notice> queryNoticeById(
            @PathVariable String id) {
        try {
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("通知ID不能为空");
            }
            Notice notice = noticeOrmService.getById(id);
            if (notice == null) {
                return ApiResult.fail("通知不存在");
            }
            return ApiResult.success(notice);
        } catch (Exception e) {
            log.error("查询通知详情失败", e);
            return ApiResult.fail("查询通知详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前用户的通知
     */
    @GetMapping("/user")
    public ApiResult<List<Notice>> queryUserNotices(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (!StringUtils.hasText(userId)) {
                return ApiResult.fail("用户未登录");
            }
            List<Notice> notices = noticeOrmService.queryUserNotices(userId, limit);
            return ApiResult.success(notices);
        } catch (Exception e) {
            log.error("查询用户通知失败", e);
            return ApiResult.fail("查询用户通知失败: " + e.getMessage());
        }
    }

    /**
     * 标记通知为已读
     */
    @PostMapping("/read/{id}")
    public ApiResult<Boolean> markAsRead(
            @PathVariable String id) {
        try {
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("通知ID不能为空");
            }
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (!StringUtils.hasText(userId)) {
                return ApiResult.fail("用户未登录");
            }
            Notice notice = noticeOrmService.getById(id);
            if (notice == null || notice.getDelStatus() == 1) {
                return ApiResult.fail("通知不存在");
            }
            noticeOrmService.markAsRead(id, userId);
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("标记通知已读失败", e);
            return ApiResult.fail("标记通知已读失败: " + e.getMessage());
        }
    }

    /**
     * 标记所有通知为已读
     */
    @PostMapping("/read/all")
    public ApiResult<Boolean> markAllAsRead() {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (!StringUtils.hasText(userId)) {
                return ApiResult.fail("用户未登录");
            }
            long unreadCount = noticeOrmService.countUnreadByReceiver(userId);
            if (unreadCount == 0) {
                return ApiResult.success(false);
            }
            noticeOrmService.markAllAsRead(userId, userId);
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("标记所有通知已读失败", e);
            return ApiResult.fail("标记所有通知已读失败: " + e.getMessage());
        }
    }

    /**
     * 创建通知
     */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Notice> createNotice(@RequestBody Notice notice) {
        try {
            // 参数校验
            if (notice == null) {
                return ApiResult.fail("通知信息不能为空");
            }
            if (!StringUtils.hasText(notice.getReceiver())) {
                return ApiResult.fail("接收人不能为空");
            }
            if (!StringUtils.hasText(notice.getNoticeTitle())) {
                return ApiResult.fail("通知标题不能为空");
            }

            // 设置通知信息
            notice.setId(String.valueOf(UIDGenerator.generate()));
            notice.setStatus("unread");
            notice.setDelStatus(0);
            notice.setDeleteAt(DateUtils.defaultDeleteAt());

            // 设置创建和更新信息
            String userId = SecurityContext.getCurrentUser().getUserId();
            String userName = SecurityContext.getCurrentUser().getUserName();
            notice.setCreator(userId);
            notice.setCreatorName(userName);
            notice.setUpdater(userId);
            notice.setUpdaterName(userName);
            notice.setCreateAt(new Date());
            notice.setUpdateAt(new Date());

            // 保存通知
            noticeOrmService.create(notice);
            return ApiResult.success(notice);
        } catch (Exception e) {
            log.error("创建通知失败", e);
            return ApiResult.fail("创建通知失败: " + e.getMessage());
        }
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Boolean> deleteNotice(@PathVariable String id) {
        try {
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("通知ID不能为空");
            }

            Notice notice = noticeOrmService.getById(id);
            if (notice == null) {
                return ApiResult.fail("通知不存在");
            }

            // 逻辑删除
            notice.setDelStatus(1);
            notice.setDeleteAt(new Date());
            notice.setUpdateAt(new Date());
            notice.setUpdater(SecurityContext.getCurrentUser().getUserId());
            notice.setUpdaterName(SecurityContext.getCurrentUser().getUserName());

            noticeOrmService.logicalDelete(id, notice.getUpdater(), notice.getUpdaterName());
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("删除通知失败", e);
            return ApiResult.fail("删除通知失败: " + e.getMessage());
        }
    }
}
