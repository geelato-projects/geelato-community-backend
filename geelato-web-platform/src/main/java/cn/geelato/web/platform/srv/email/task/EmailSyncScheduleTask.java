package cn.geelato.web.platform.srv.email.task;

import cn.geelato.meta.UserEmailAccount;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import cn.geelato.security.SecurityContextRunnable;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.web.platform.srv.email.service.EmailSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件定时同步任务
 * <p>
 * 每分钟检查一次所有已开启同步的邮箱账号，
 * 若达到同步间隔则触发增量同步。
 * </p>
 *
 * @see EmailSyncService
 */
@Component
@ConditionalOnProperty(name = "geelato.email.sync.enabled", havingValue = "true")
@Slf4j
public class EmailSyncScheduleTask {

    private static final int DEFAULT_INTERVAL_MINUTES = 5;

    @Autowired
    private EmailSyncService emailSyncService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void syncAllEnabledAccounts() {
        try {
            List<Map<String, Object>> accounts = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("syncEnabled", 1),
                            Filter.eq("enableStatus", 1),
                            Filter.eq("delStatus", 0)
                    )
                    .order(Order.asc("createAt"))
                    .list();

            if (accounts == null || accounts.isEmpty()) {
                return;
            }

            log.debug("邮件同步定时任务: 发现 {} 个待同步账号", accounts.size());

            for (Map<String, Object> row : accounts) {
                try {
                    checkAndSync(row);
                } catch (Exception e) {
                    String accountId = Objects.toString(row.get("id"), "?");
                    log.warn("邮箱账号 {} 同步检查异常", accountId, e);
                }
            }
        } catch (Exception e) {
            log.error("邮件同步定时任务执行异常", e);
        }
    }

    private void checkAndSync(Map<String, Object> row) {
        String accountId = Objects.toString(row.get("id"), null);
        String userId = Objects.toString(row.get("userId"), null);
        String syncStatus = Objects.toString(row.get("syncStatus"), "idle");

        if (accountId == null || userId == null) {
            return;
        }

        // 跳过正在同步的账号
        if ("syncing".equals(syncStatus)) {
            log.debug("邮箱账号 {} 正在同步中，跳过", accountId);
            return;
        }

        // 检查是否达到同步间隔
        Object lastSyncAtObj = row.get("lastSyncAt");
        if (lastSyncAtObj instanceof Date lastSyncAt) {
            int intervalMinutes = toInt(row.get("syncIntervalMinutes"), DEFAULT_INTERVAL_MINUTES);
            long intervalMs = (long) intervalMinutes * 60 * 1000;
            long elapsed = System.currentTimeMillis() - lastSyncAt.getTime();
            if (elapsed < intervalMs) {
                log.debug("邮箱账号 {} 未到同步间隔(剩余{}ms)", accountId, intervalMs - elapsed);
                return;
            }
        }

        log.info("触发邮箱账号 {} 定时同步", accountId);
        // 异步执行同步，避免阻塞定时任务；构造系统用户上下文传播到异步线程
        User systemUser = new User();
        systemUser.setUserId(userId);
        systemUser.setUserName("system-sync");
        CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> {
            try {
                emailSyncService.syncAccount(userId, accountId);
            } catch (Exception e) {
                log.error("邮箱账号 {} 异步同步失败", accountId, e);
            }
        }, systemUser, new Tenant("system")));
    }

    private int toInt(Object v, int defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
