package cn.geelato.web.platform.srv.notification.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.Notification;
import cn.geelato.meta.NotificationOutbox;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.srv.notification.channel.DeliveryChannelManager;
import cn.geelato.web.platform.srv.notification.config.NotificationProperties;
import cn.geelato.web.platform.srv.notification.dto.NotifyRequest;
import cn.geelato.web.platform.srv.notification.enums.NotificationChannelEnum;
import cn.geelato.web.platform.srv.notification.enums.OutboxStatusEnum;
import cn.geelato.web.platform.srv.notification.enums.SenderTypeEnum;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 通知编排服务：消息管理 + 投递编排入口。
 * <p>
 * {@link #dispatch(NotifyRequest)} 在业务事务内：
 * <ol>
 *   <li>写通知主体 platform_notification（按 biz_type+biz_id 幂等）</li>
 *   <li>按请求渠道写发件箱 platform_notification_outbox，每个渠道一行（status=ready）</li>
 * </ol>
 * 实际投递由 {@code NotificationOutboxScheduler} 异步扫描 ready 项，路由到对应 {@link DeliveryChannel} 执行。
 *
 * @author geelato
 */
@Service
@Slf4j
public class NotificationService extends BaseService {

    private final NotificationProperties properties;
    private final DeliveryChannelManager channelManager;

    @Autowired
    public NotificationService(@Qualifier("primaryDao") Dao dao,
                               NotificationProperties properties,
                               DeliveryChannelManager channelManager) {
        this.dao = dao;
        this.properties = properties;
        this.channelManager = channelManager;
    }

    /**
     * 发起通知：写主体 + 按渠道写 outbox。返回主体 id。
     * 一次成功调用 = 一条通知（无业务幂等：同 biz 再次调用即再次通知，如再次提醒）。
     * 事务保证主体与 outbox 一致写入；投递异步进行。
     */
    @Transactional(rollbackFor = Exception.class)
    public String dispatch(NotifyRequest request) {
        validate(request);

        String tenantCode = resolveTenantCode();
        List<String> channels = request.resolveChannels();
        List<String> recipients = request.getRecipients();
        Notification notification = createModel(buildNotification(request, tenantCode, channels));
        Date now = new Date();
        for (String channel : channels) {
            if (!isChannelAvailable(channel)) {
                log.warn("渠道 {} 不可用（无对应投递方实现或未启用），跳过写 outbox", channel);
                continue;
            }
            NotificationOutbox outbox = new NotificationOutbox();
            // 不预置 id：id 为空才会走 INSERT（平台 ORM 语义）
            outbox.setNotificationId(notification.getId());
            outbox.setChannel(channel);
            outbox.setRecipientJson(JSON.toJSONString(recipients));
            outbox.setStatus(OutboxStatusEnum.READY.value());
            outbox.setRetryCount(0);
            outbox.setDelStatus(ColumnDefault.DEL_STATUS_VALUE);
            outbox.setDeleteAt(DateUtils.defaultDeleteAt());
            outbox.setCreateAt(now);
            outbox.setUpdateAt(now);
            outbox.setCreator(notification.getCreator());
            outbox.setUpdater(notification.getCreator());
            outbox.setTenantCode(tenantCode);
            dao.save(outbox);
        }
        return notification.getId();
    }

    private void validate(NotifyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("通知请求不能为空");
        }
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            throw new IllegalArgumentException("收件人不能为空");
        }
        if (Strings.isBlank(request.getTitle())) {
            throw new IllegalArgumentException("通知标题不能为空");
        }
    }

    private Notification buildNotification(NotifyRequest request, String tenantCode, List<String> channels) {
        Date now = new Date();
        String operator = resolveOperator();
        String senderId = Strings.isBlank(request.getSenderId()) ? operator : request.getSenderId();
        String senderName = resolveSenderName(request, operator);
        SenderTypeEnum senderType = request.resolveSenderType();

        Notification n = new Notification();
        n.setTitle(request.getTitle());
        n.setContent(request.getContent());
        n.setSenderId(senderId);
        n.setSenderName(senderName);
        n.setSenderType(senderType.value());
        n.setBizType(request.getBizType());
        n.setBizId(request.getBizId());
        n.setActionUrl(request.getActionUrl());
        n.setChannels(JSON.toJSONString(channels));
        n.setPriority(request.getPriority());
        n.setDelStatus(ColumnDefault.DEL_STATUS_VALUE);
        n.setDeleteAt(DateUtils.defaultDeleteAt());
        n.setCreateAt(now);
        n.setUpdateAt(now);
        n.setCreator(operator);
        n.setCreatorName(senderName);
        n.setUpdater(operator);
        n.setUpdaterName(senderName);
        n.setTenantCode(tenantCode);
        return n;
    }

    /** 渠道是否可用：inapp 内置必然可用；其他渠道需存在对应 DeliveryChannel 实现（SPI 扩展） */
    private boolean isChannelAvailable(String channel) {
        if (NotificationChannelEnum.INAPP.value().equalsIgnoreCase(channel)) {
            return true;
        }
        return channelManager.hasChannel(channel);
    }

    private String resolveTenantCode() {
        try {
            String tc = SessionCtx.getCurrentTenantCode();
            return Strings.isNotBlank(tc) ? tc : "geelato";
        } catch (Exception e) {
            return "geelato";
        }
    }

    private String resolveOperator() {
        try {
            String uid = SecurityContext.getCurrentUser().getUserId();
            return Strings.isNotBlank(uid) ? uid : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    private String resolveSenderName(NotifyRequest request, String operator) {
        if (Strings.isNotBlank(request.getSenderName())) {
            return request.getSenderName();
        }
        try {
            String name = SecurityContext.getCurrentUser().getUserName();
            return Strings.isNotBlank(name) ? name : operator;
        } catch (Exception e) {
            return request.resolveSenderType() == SenderTypeEnum.SYSTEM ? "system" : operator;
        }
    }
}
