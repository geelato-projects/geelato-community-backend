package cn.geelato.web.platform.srv.notification.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.meta.NotificationUser;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 通知收件人状态服务。
 * 围绕 {@link NotificationUser}（platform_notification_user）提供：
 * <ul>
 *   <li>站内信 fan-out：为每个收件人写一行未读状态（幂等）</li>
 *   <li>收件箱查询：按 user_id + read_status + archived 分页</li>
 *   <li>已读/全部已读/星标/归档（带归属校验，防越权）</li>
 *   <li>未读数统计（铃铛角标）</li>
 * </ul>
 *
 * @author geelato
 */
@Service
@Slf4j
public class NotificationUserService extends BaseService {

    @Autowired
    public NotificationUserService(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    /**
     * 站内信 fan-out：为每个 userId 写入一行未读收件人状态（已存在则跳过，靠 uk_notif_user 幂等）。
     * 使用 batchUpdate 批量插入，群发 N 人仅需 1 次 DB 往返。
     */
    public void fanOutToInbox(String notificationId, List<String> userIds, String operator) {
        if (userIds == null || userIds.isEmpty() || Strings.isBlank(notificationId)) {
            return;
        }
        Date now = new Date();
        Date defaultDeleteAt = DateUtils.defaultDeleteAt();
        List<Object[]> batch = new ArrayList<>();
        for (String userId : userIds) {
            if (Strings.isBlank(userId)) {
                continue;
            }
            batch.add(new Object[]{
                    String.valueOf(UIDGenerator.generate()), notificationId, userId,
                    0, null, 0, 0,
                    ColumnDefault.DEL_STATUS_VALUE, now, operator, now, operator,
                    defaultDeleteAt
            });
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            dao.getJdbcTemplate().batchUpdate(
                    "INSERT INTO platform_notification_user "
                            + "(id, notification_id, user_id, read_status, read_at, starred, archived, "
                            + "del_status, create_at, creator, update_at, updater, delete_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    batch);
        } catch (Exception e) {
            // 批量插入可能因部分行命中 uk_notif_user 唯一键而整体失败，降级为逐行插入以保证幂等
            log.debug("批量插入收件人状态失败，降级逐行：notificationId={}, reason={}", notificationId, e.getMessage());
            for (String userId : userIds) {
                if (Strings.isBlank(userId)) {
                    continue;
                }
                NotificationUser nu = new NotificationUser();
                nu.setId(String.valueOf(UIDGenerator.generate()));
                nu.setNotificationId(notificationId);
                nu.setUserId(userId);
                nu.setReadStatus(0);
                nu.setStarred(0);
                nu.setArchived(0);
                nu.setDelStatus(ColumnDefault.DEL_STATUS_VALUE);
                nu.setDeleteAt(defaultDeleteAt);
                nu.setCreateAt(now);
                nu.setUpdateAt(now);
                nu.setCreator(operator);
                nu.setUpdater(operator);
                try {
                    dao.save(nu);
                } catch (Exception ignored) {
                    // 命中 uk_notif_user 唯一键（重复投递）属预期，忽略
                }
            }
        }
    }

    /**
     * 收件箱分页查询（当前用户）。前端通常传入 read_status / archived 等过滤条件。
     */
    public ApiPagedResult pageQueryInbox(Class<NotificationUser> entity, FilterGroup filter, PageQueryRequest request) {
        return pageQueryModel(entity, filter, request);
    }

    /**
     * 当前用户未读数（铃铛角标）。
     * 使用 COUNT(*) 而非全量拉取，走 idx_user_unread(user_id, read_status, archived) 索引。
     */
    public long countUnread(String userId) {
        Long count = dao.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM platform_notification_user WHERE del_status = 0 AND user_id = ? AND read_status = 0",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    /**
     * 标记单条已读（带归属校验，只能标记自己的）。
     *
     * @return true 表示成功；false 表示不存在或不属于该用户
     */
    public boolean markRead(String notificationUserId, String userId) {
        FilterGroup fg = new FilterGroup()
                .addFilter("id", notificationUserId)
                .addFilter("userId", userId);
        dao.setDefaultFilter(true, filterGroup);
        List<NotificationUser> list = dao.queryList(NotificationUser.class, fg, null);
        if (list == null || list.isEmpty()) {
            return false;
        }
        NotificationUser nu = list.get(0);
        nu.setReadStatus(1);
        nu.setReadAt(new Date());
        nu.setUpdateAt(new Date());
        nu.setUpdater(userId);
        dao.save(nu);
        return true;
    }

    /**
     * 标记当前用户所有未读为已读。
     * 单条 UPDATE 批量更新，避免逐行 dao.save 的 N+1 写入。
     */
    public int markAllRead(String userId) {
        Date now = new Date();
        return dao.getJdbcTemplate().update(
                "UPDATE platform_notification_user SET read_status = 1, read_at = ?, update_at = ?, updater = ? "
                        + "WHERE del_status = 0 AND user_id = ? AND read_status = 0",
                now, now, userId, userId);
    }

    /**
     * 设置星标/归档状态（带归属校验）。
     */
    public boolean updateFlag(String notificationUserId, String userId, String field, int value) {
        FilterGroup fg = new FilterGroup()
                .addFilter("id", notificationUserId)
                .addFilter("userId", userId);
        dao.setDefaultFilter(true, filterGroup);
        List<NotificationUser> list = dao.queryList(NotificationUser.class, fg, null);
        if (list == null || list.isEmpty()) {
            return false;
        }
        NotificationUser nu = list.get(0);
        if ("starred".equals(field)) {
            nu.setStarred(value);
        } else if ("archived".equals(field)) {
            nu.setArchived(value);
        } else {
            return false;
        }
        nu.setUpdateAt(new Date());
        nu.setUpdater(userId);
        dao.save(nu);
        return true;
    }
}
