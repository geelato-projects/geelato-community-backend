package cn.geelato.meta;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台通知主体，一封通知一行。
 * 与 {@link NotificationUser}（收件人状态）分离：一封通知可发给多人，每人独立已读状态。
 * 与 {@link NotificationOutbox}（投递编排发件箱）分离：按渠道异步、可靠投递。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_notification", catalog = "platform")
@Title(title = "平台通知")
public class Notification extends BaseEntity {

    @Title(title = "标题")
    @Col(name = "title", nullable = false, charMaxlength = 128)
    private String title;

    @Title(title = "内容")
    @Col(name = "content")
    private String content;

    @Title(title = "发送者ID", description = "发送者 userId；系统发送为 system")
    @Col(name = "sender_id", charMaxlength = 64)
    private String senderId;

    @Title(title = "发送者名称")
    @Col(name = "sender_name", charMaxlength = 64)
    private String senderName;

    @Title(title = "发送者类型", description = "system | user")
    @Col(name = "sender_type", charMaxlength = 16)
    private String senderType;

    @Title(title = "业务类型", description = "order/contract/task 等业务标识")
    @Col(name = "biz_type", charMaxlength = 32)
    private String bizType;

    @Title(title = "业务主键")
    @Col(name = "biz_id", charMaxlength = 64)
    private String bizId;

    @Title(title = "跳转地址", description = "点击通知跳转地址，前端 router.push 或 window.open")
    @Col(name = "action_url", charMaxlength = 512)
    private String actionUrl;

    @Title(title = "投递渠道", description = "实际投递渠道快照 JSON，如 [\"inapp\",\"email\"]")
    @Col(name = "channels", charMaxlength = 256)
    private String channels;

    @Title(title = "优先级")
    @Col(name = "priority")
    private int priority;
}
