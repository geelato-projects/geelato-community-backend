package cn.geelato.web.platform.srv.notification.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.meta.Notification;
import cn.geelato.meta.NotificationUser;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.Order;
import cn.geelato.orm.page.PageResult;
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
import java.util.Map;

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
                // 不预置 id：平台 ORM 以 id 是否为空决定 INSERT/UPDATE，预置会静默变 UPDATE
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
     * 收件箱分页查询（当前用户）：收件人状态 JOIN 通知主体（MetaFactory DSL），返回可直接渲染的扁平行。
     * <ul>
     *   <li>userId 服务端强制，防越权</li>
     *   <li>撤回语义：主体 del_status=1 的通知因 JOIN 条件自动从收件箱消失</li>
     *   <li>排序固定主体创建时间倒序，不采纳客户端 orderBy（防注入）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public ApiPagedResult pageQueryInbox(String userId, Integer readStatus, Integer archived,
                                         String bizType, String keyword, PageQueryRequest request) {
        int pageNum = Math.max(1, request.getPageNum());
        int pageSize = Math.min(Math.max(1, request.getPageSize()), 100);

        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        if (readStatus != null) {
            filters.add(Filter.eq("readStatus", readStatus));
        }
        if (archived != null) {
            filters.add(Filter.eq("archived", archived));
        }
        // bizType 属主体字段，先查主体 id 再 in 过滤
        if (Strings.isNotBlank(bizType)) {
            List<String> subjectIds = querySubjectIds(
                    Filter.eq("bizType", bizType.trim()), Filter.eq("delStatus", 0));
            if (subjectIds.isEmpty()) {
                return new PageResult<Map<String, Object>>(pageNum, pageSize, 0).toApiPagedResult();
            }
            filters.add(Filter.in("notificationId", subjectIds.toArray()));
        }
        // 标题/内容模糊搜索（OR 语义：两次 like 预查后合并去重）
        if (Strings.isNotBlank(keyword)) {
            String kw = keyword.trim();
            List<String> subjectIds = querySubjectIds(Filter.eq("delStatus", 0), Filter.like("title", kw));
            subjectIds.addAll(querySubjectIds(Filter.eq("delStatus", 0), Filter.like("content", kw)));
            List<String> distinctIds = subjectIds.stream().distinct().toList();
            if (distinctIds.isEmpty()) {
                return new PageResult<Map<String, Object>>(pageNum, pageSize, 0).toApiPagedResult();
            }
            filters.add(Filter.in("notificationId", distinctIds.toArray()));
        }

        PageResult<Map<String, Object>> page = MetaFactory.query(NotificationUser.class)
                .disableInjectFilter()
                .as("nu")
                .select(new String[]{"id", "notificationId", "readStatus", "readAt", "starred", "archived"})
                // 主体字段经 JOIN 带出；createAt 取主体创建时间（通知发生时间）
                .selectExpr("n.title", "title")
                .selectExpr("n.content", "content")
                .selectExpr("n.sender_id", "senderId")
                .selectExpr("n.sender_name", "senderName")
                .selectExpr("n.sender_type", "senderType")
                .selectExpr("n.biz_type", "bizType")
                .selectExpr("n.biz_id", "bizId")
                .selectExpr("n.action_url", "actionUrl")
                .selectExpr("n.priority", "priority")
                .selectExpr("n.create_at", "createAt")
                .innerJoin(Notification.class, "n", on -> on
                        .eqField("notificationId", "n.id")
                        .raw("n.del_status = 0"))
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("createAt"))
                .page(pageNum, pageSize)
                .page();
        return page.toApiPagedResult();
    }

    private List<String> querySubjectIds(Filter... filters) {
        List<String> ids = MetaFactory.query(Notification.class)
                .disableInjectFilter()
                .select(new String[]{"id"})
                .where(filters)
                .oneColumn(String.class);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    /**
     * 当前用户未读数（铃铛角标）。
     * 行的 creator 是投递操作者而非收件人，需 disableInjectFilter 跳过数据权限注入。
     */
    public long countUnread(String userId) {
        return MetaFactory.query(NotificationUser.class)
                .disableInjectFilter()
                .where(
                        Filter.eq("userId", userId),
                        Filter.eq("readStatus", 0),
                        Filter.eq("delStatus", 0)
                )
                .count();
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
     * 删除当前用户收件箱中的一条通知（逻辑删收件人状态行，不影响其他收件人）。
     *
     * @return true 成功；false 不存在或不属于该用户
     */
    public boolean deleteInbox(String notificationUserId, String userId) {
        boolean exists = MetaFactory.query(NotificationUser.class)
                .disableInjectFilter()
                .where(Filter.eq("id", notificationUserId),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0))
                .exists();
        if (!exists) {
            return false;
        }
        MetaFactory.update(NotificationUser.class)
                .where(Filter.eq("id", notificationUserId), Filter.eq("userId", userId))
                .value("delStatus", 1)
                .value("deleteAt", new Date())
                .value("updater", userId)
                .save();
        return true;
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
