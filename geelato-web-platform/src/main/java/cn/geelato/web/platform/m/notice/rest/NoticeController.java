package cn.geelato.web.platform.m.notice.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.notice.entity.Notice;
import cn.geelato.web.platform.m.notice.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    @Autowired
    private NoticeMapper noticeMapper;

    /**
     * 查询通知列表
     */
    @GetMapping("/list")
    public ApiResult<List<Notice>> queryNoticeList(
            @RequestParam(required = false) String receiver,
            @RequestParam(required = false) String noticeTitle,
            @RequestParam(required = false) String status) {
        try {
            QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
            if (StringUtils.hasText(receiver)) {
                queryWrapper.like("receiver", receiver);
            }
            if (StringUtils.hasText(noticeTitle)) {
                queryWrapper.like("notice_title", noticeTitle);
            }
            if (StringUtils.hasText(status)) {
                queryWrapper.eq("status", status);
            }
            queryWrapper.eq("del_status", 0);
            queryWrapper.orderByDesc("create_at");
            List<Notice> notices = noticeMapper.selectList(queryWrapper);
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
            Notice notice = noticeMapper.selectById(id);
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
            List<Notice> notices = noticeMapper.selectUserNotices(userId, limit);
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
            int result = noticeMapper.markAsRead(id, userId);
            if (result > 0) {
                return ApiResult.success(true);
            } else {
                return ApiResult.fail("标记已读失败");
            }
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
            int result = noticeMapper.markAllAsRead(userId, userId);
            return ApiResult.success(result > 0);
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
            int result = noticeMapper.insert(notice);
            if (result > 0) {
                return ApiResult.success(notice);
            } else {
                return ApiResult.fail("创建通知失败");
            }
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

            Notice notice = noticeMapper.selectById(id);
            if (notice == null) {
                return ApiResult.fail("通知不存在");
            }

            // 逻辑删除
            notice.setDelStatus(1);
            notice.setDeleteAt(new Date());
            notice.setUpdateAt(new Date());
            notice.setUpdater(SecurityContext.getCurrentUser().getUserId());
            notice.setUpdaterName(SecurityContext.getCurrentUser().getUserName());

            int result = noticeMapper.updateById(notice);
            if (result > 0) {
                return ApiResult.success(true);
            } else {
                return ApiResult.fail("删除通知失败");
            }
        } catch (Exception e) {
            log.error("删除通知失败", e);
            return ApiResult.fail("删除通知失败: " + e.getMessage());
        }
    }
}
