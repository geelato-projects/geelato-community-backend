package cn.geelato.web.platform.m.notice.mapper;

import cn.geelato.web.platform.m.notice.entity.Notice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知管理Mapper接口
 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    /**
     * 分页查询通知列表
     * @param page 分页对象
     * @param receiver 接收人
     * @param noticeTitle 通知标题
     * @param status 状态
     * @return 分页结果
     */
    Page<Notice> selectNoticeList(Page<Notice> page,
                                 @Param("receiver") String receiver,
                                 @Param("noticeTitle") String noticeTitle,
                                 @Param("status") String status);

    /**
     * 查询用户的通知列表
     * @param receiver 接收人
     * @param limit 限制条数
     * @return 通知列表
     */
    List<Notice> selectUserNotices(@Param("receiver") String receiver, @Param("limit") Integer limit);
    
    /**
     * 标记通知为已读
     * @param id 通知ID
     * @param updater 更新人
     * @return 更新结果
     */
    int markAsRead(@Param("id") String id, @Param("updater") String updater);
    
    /**
     * 标记用户所有通知为已读
     * @param receiver 接收人
     * @param updater 更新人
     * @return 更新结果
     */
    int markAllAsRead(@Param("receiver") String receiver, @Param("updater") String updater);
}