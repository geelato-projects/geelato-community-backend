package cn.geelato.meta;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 页面签出申请接管请求实体
 * @author itechgee@126.com
 */
@Getter
@Setter
@Entity(name = "platform_app_page_check_req", catalog = "platform")
@Title(title = "页面签出申请接管请求")
public class AppPageCheckReq extends BaseEntity {
    @Title(title = "页面ID")
    @Col(name = "page_id", nullable = false)
    private String pageId;
    @Title(title = "页面标题")
    @Col(name = "page_title", nullable = false)
    private String pageTitle;
    @Title(title = "发起用户ID")
    @Col(name = "requester_id", nullable = false)
    private String requesterId;
    @Title(title = "发起用户名称")
    @Col(name = "requester_name", nullable = false)
    private String requesterName;
    @Title(title = "目标用户ID")
    @Col(name = "target_user_id", nullable = false)
    private String targetUserId;
    @Title(title = "目标用户名称")
    @Col(name = "target_user_name", nullable = false)
    private String targetUserName;
    @Title(title = "请求时间")
    @Col(name = "request_time", nullable = false)
    private java.util.Date requestTime;
    @Title(title = "请求状态", description = "pending/approved/rejected/expired")
    @Col(name = "status", nullable = false)
    private String status;
    @Title(title = "处理时间")
    @Col(name = "process_time", nullable = true)
    private java.util.Date processTime;
    @Title(title = "处理用户ID")
    @Col(name = "process_user_id", nullable = true)
    private String processUserId;
    @Title(title = "处理用户名称")
    @Col(name = "process_user_name", nullable = true)
    private String processUserName;
    @Title(title = "处理备注")
    @Col(name = "process_comment", nullable = true, dataType = "varchar(500)")
    private String processComment;
}