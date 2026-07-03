package cn.geelato.web.platform.srv.notice.service;

import cn.geelato.meta.Notice;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class NoticeOrmService {

    public List<Notice> queryNoticeList(String receiver, String noticeTitle, String status) {
        List<Filter> filters = new ArrayList<>();
        if (StringUtils.hasText(receiver)) {
            filters.add(Filter.like("receiver", receiver));
        }
        if (StringUtils.hasText(noticeTitle)) {
            filters.add(Filter.like("noticeTitle", noticeTitle));
        }
        if (StringUtils.hasText(status)) {
            filters.add(Filter.eq("status", status));
        }
        filters.add(Filter.eq("delStatus", 0));
        return MetaFactory.query(Notice.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("createAt"))
                .wrapperResult(this::toNotice)
                .list();
    }

    public Notice getById(String id) {
        return MetaFactory.query(Notice.class)
                .where(Filter.eq("id", id))
                .wrapperResult(this::toNotice)
                .one();
    }

    public List<Notice> queryUserNotices(String receiver, Integer limit) {
        var query = MetaFactory.query(Notice.class)
                .where(Filter.eq("delStatus", 0), Filter.like("receiver", receiver))
                .order(Order.desc("createAt"))
                .wrapperResult(this::toNotice);
        if (limit != null && limit > 0) {
            query.page(1, limit);
        }
        return query.list();
    }

    public long countUnreadByReceiver(String receiver) {
        return MetaFactory.query(Notice.class)
                .where(
                        Filter.like("receiver", receiver),
                        Filter.ne("status", "read"),
                        Filter.eq("delStatus", 0)
                )
                .count();
    }

    public String create(Notice notice) {
        String id = MetaFactory.insert(Notice.class)
                .values(toValueMap(notice))
                .save();
        notice.setId(id);
        return id;
    }

    public String markAsRead(String id, String updater) {
        return MetaFactory.update(Notice.class)
                .where(Filter.eq("id", id), Filter.eq("delStatus", 0))
                .value("status", "read")
                .value("updater", updater)
                .save();
    }

    public String markAllAsRead(String receiver, String updater) {
        return MetaFactory.update(Notice.class)
                .where(
                        Filter.like("receiver", receiver),
                        Filter.ne("status", "read"),
                        Filter.eq("delStatus", 0)
                )
                .value("status", "read")
                .value("updater", updater)
                .save();
    }

    public String logicalDelete(String id, String updater, String updaterName) {
        return MetaFactory.update(Notice.class)
                .where(Filter.eq("id", id))
                .value("delStatus", 1)
                .value("deleteAt", new Date())
                .value("updater", updater)
                .value("updaterName", updaterName)
                .save();
    }

    private Notice toNotice(Map<String, Object> row) {
        return row == null || row.isEmpty() ? null : JSON.parseObject(JSON.toJSONString(row), Notice.class);
    }

    private Map<String, Object> toValueMap(Notice notice) {
        Map<String, Object> valueMap = JSON.parseObject(JSON.toJSONString(notice), Map.class);
        valueMap.values().removeIf(value -> value == null);
        return valueMap;
    }
}
