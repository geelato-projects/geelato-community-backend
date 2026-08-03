package cn.geelato.web.platform.srv.notification.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.orm.Dao;
import cn.geelato.meta.Notification;
import cn.geelato.meta.NotificationOutbox;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
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
     * 事务保证主体与 outbox 一致写入；投递异步进行。
     */
    @Transactional(rollbackFor = Exception.class)
    public String dispatch(NotifyRequest request) {
        validate(request);

        String tenantCode = resolveTenantCode();
        List<String> channels = request.resolveChannels();
        List<String> recipients = request.getRecipients();

        // 1. 写通知主体（幂等：同租户同 bizType+bizId 已存在则复用）
        Notification notification = buildNotification(request, tenantCode, channels);
        Notification existing = findExistingByBiz(tenantCode, request.getBizType(), request.getBizId());
        if (existing != null) {
            notification = existing;
            log.info("通知主体已存在（业务幂等），复用 id={}, bizType={}, bizId={}", existing.getId(), request.getBizType(), request.getBizId());
        } else {
            dao.save(notification);
        }

        // 2. 按渠道写 outbox（每渠道一行，独立投递/重试，单渠道失败不影响其他）
        Date now = new Date();
        for (String channel : channels) {
            if (!isChannelAvailable(channel)) {
                log.warn("渠道 {} 不可用（无对应投递方实现或未启用），跳过写 outbox", channel);
                continue;
            }
            NotificationOutbox outbox = new NotificationOutbox();
            outbox.setId(String.valueOf(UIDGenerator.generate()));
            outbox.setNotificationId(notification.getId());
            outbox.setChannel(channel);
            outbox.setRecipientJson(JSON.toJSONString(recipients));
            outbox.setStatus(OutboxStatusEnum.READY.value());
            outbox.setRetryCount(0);
            outbox.setIdempotencyKey(buildIdempotencyKey(tenantCode, notification.getId(), channel, request.getIdempotencyKey()));
            outbox.setDelStatus(ColumnDefault.DEL_STATUS_VALUE);
            outbox.setDeleteAt(DateUtils.defaultDeleteAt());
            outbox.setCreateAt(now);
            outbox.setUpdateAt(now);
            outbox.setCreator(notification.getCreator());
            outbox.setUpdater(notification.getCreator());
            outbox.setTenantCode(tenantCode);
            try {
                dao.save(outbox);
            } catch (Exception e) {
                // 幂等键命中（重复 dispatch），跳过
                log.debug("outbox 已存在（幂等），跳过：notificationId={}, channel={}", notification.getId(), channel);
            }
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
        n.setId(String.valueOf(UIDGenerator.generate()));
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

    /** 业务幂等：按租户+bizType+bizId 查主体 */
    private Notification findExistingByBiz(String tenantCode, String bizType, String bizId) {
        if (Strings.isBlank(bizType) || Strings.isBlank(bizId)) {
            return null;
        }
        try {
            return dao.queryForObject(Notification.class, "bizType", bizType, "bizId", bizId);
        } catch (Exception e) {
            // 多条/无条均按不存在处理
            return null;
        }
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

    private String buildIdempotencyKey(String tenantCode, String notificationId, String channel, String bizKey) {
        if (Strings.isNotBlank(bizKey)) {
            return tenantCode + ":" + channel + ":" + bizKey;
        }
        return tenantCode + ":" + channel + ":" + notificationId;
    }
}
