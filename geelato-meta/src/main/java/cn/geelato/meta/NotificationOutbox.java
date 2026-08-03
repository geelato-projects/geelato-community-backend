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
 * 通知投递编排发件箱，每个投递渠道一行。
 * 事务性 outbox：业务事务内写入（ready），由调度器异步扫描、抢占（CAS→processing）、
 * 调用对应 {@code DeliveryChannel} 投递，成功→success，失败→指数退避重试→死信（dead）。
 * 单渠道失败不影响其他渠道（每渠道独立一行）。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_notification_outbox", catalog = "platform")
@Title(title = "通知投递发件箱")
public class NotificationOutbox extends BaseEntity {

    @Title(title = "通知ID")
    @Col(name = "notification_id", charMaxlength = 32, nullable = false)
    private String notificationId;

    @Title(title = "投递渠道", description = "inapp | email | sms | wecom ...")
    @Col(name = "channel", charMaxlength = 32, nullable = false)
    private String channel;

    @Title(title = "收件人", description = "userId 列表 JSON，如 [\"u1\",\"u2\"]")
    @Col(name = "recipient_json", charMaxlength = 2048, nullable = false)
    private String recipientJson;

    @Title(title = "投递状态", description = "ready | processing | success | fail | dead")
    @Col(name = "status", charMaxlength = 16)
    private String status;

    @Title(title = "重试次数")
    @Col(name = "retry_count")
    private int retryCount;

    @Title(title = "下次重试时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "next_retry_at")
    private Date nextRetryAt;

    @Title(title = "幂等键")
    @Col(name = "idempotency_key", charMaxlength = 128)
    private String idempotencyKey;

    @Title(title = "错误信息")
    @Col(name = "error_msg", charMaxlength = 512)
    private String errorMsg;
}
