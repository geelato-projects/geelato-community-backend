package cn.geelato.meta;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import cn.geelato.utils.DateUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 通知收件人状态，每个收件人一行。
 * 与 {@link Notification}（主体）normalized 分离，使每人独立已读/星标/归档状态，
 * 互不影响（一人已读不影响他人），并支持按用户维度的收件箱查询。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_notification_user", catalog = "platform")
@Title(title = "通知收件人状态")
public class NotificationUser extends BaseEntity {

    @Title(title = "通知ID")
    @Col(name = "notification_id", charMaxlength = 32, nullable = false)
    private String notificationId;

    @Title(title = "收件人ID")
    @Col(name = "user_id", charMaxlength = 64, nullable = false)
    private String userId;

    @Title(title = "已读状态", description = "0：未读、1：已读")
    @Col(name = "read_status")
    private int readStatus;

    @Title(title = "已读时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "read_at")
    private Date readAt;

    @Title(title = "星标", description = "0：否、1：是")
    @Col(name = "starred")
    private int starred;

    @Title(title = "已归档", description = "0：否、1：是（用户主动归档，默认收件箱不展示）")
    @Col(name = "archived")
    private int archived;
}
