package cn.geelato.mail.task;

import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.service.MailAccountService;
import cn.geelato.mail.service.MailSyncService;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.Order;
import cn.geelato.security.SecurityContextRunnable;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 邮件定时同步任务（自 community srv/email 的 EmailSyncScheduleTask 移植）。
 *
 * <p>每分钟扫描一次 mail_account 中 sync_enabled=1 的账号，
 * 达到账号级同步间隔（sync_interval_minutes，默认 5 分钟）后异步触发增量同步。</p>
 *
 * <p><b>开关</b>：{@code geelato.mail.sync.enabled=true}（默认关闭）。定时同步属网络/DB
 * 密集型后台行为，按需开启。</p>
 *
 * <p><b>调度方式</b>：自管理 {@code ScheduledExecutorService}（守护线程），
 * 而非 Spring 的 {@code @Scheduled} + {@code @EnableScheduling}。
 * 原因：平台（scaffold 宿主）刻意不开启全局 {@code @EnableScheduling}，
 * {@code @Scheduled} 任务在这些宿主中不会运行；自管理调度与宿主无关，
 * 也避免本任务的开闭被宿主的全局调度开关误伤（对齐 NotificationOutboxScheduler 惯例）。</p>
 *
 * <p><b>会话上下文</b>：以账号归属人身份（SecurityContextRunnable 显式 User/Tenant）
 * 执行同步，落库审计字段与归属校验（getOwned）与手动同步同口径。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "geelato.mail.sync.enabled", havingValue = "true")
public class MailSyncScheduleTask {

    private static final int DEFAULT_INTERVAL_MINUTES = 5;
    private static final long SCAN_INTERVAL_SECONDS = 60;
    private static final long INITIAL_DELAY_SECONDS = 30;

    @Autowired
    private MailAccountService accountService;

    @Autowired
    private MailSyncService mailSyncService;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "mail-sync-schedule-task");
            t.setDaemon(true);
            return t;
        });
        // fixedDelay 语义：上一轮结束后等间隔再开始下一轮（与 @Scheduled(fixedDelay) 一致）
        scheduler.scheduleWithFixedDelay(this::safeScan, INITIAL_DELAY_SECONDS, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("邮件定时同步任务已启动（扫描间隔 {}s，账号级间隔默认 {} 分钟）", SCAN_INTERVAL_SECONDS, DEFAULT_INTERVAL_MINUTES);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeScan() {
        try {
            scanAllEnabledAccounts();
        } catch (Exception e) {
            log.error("邮件定时同步任务扫描异常", e);
        }
    }

    private void scanAllEnabledAccounts() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accounts = MetaFactory.query(MailAccount.class)
                .where(
                        Filter.eq("syncEnabled", 1),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("createAt"))
                .list();
        if (accounts == null || accounts.isEmpty()) {
            return;
        }
        log.debug("邮件定时同步任务: 发现 {} 个开启同步的账号", accounts.size());
        for (Map<String, Object> row : accounts) {
            try {
                checkAndSync(row);
            } catch (Exception e) {
                log.warn("邮箱账号 {} 同步检查异常", Objects.toString(row.get("id"), "?"), e);
            }
        }
    }

    private void checkAndSync(Map<String, Object> row) {
        String accountId = Objects.toString(row.get("id"), null);
        String userId = Objects.toString(row.get("userId"), null);
        String tenantCode = Objects.toString(row.get("tenantCode"), "geelato");
        String lastSyncStatus = Objects.toString(row.get("lastSyncStatus"), null);

        if (accountId == null || userId == null) {
            return;
        }
        // 跳过正在同步的账号（异步任务未完成时不重叠触发）
        if ("syncing".equals(lastSyncStatus)) {
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
        // 异步执行同步，避免阻塞扫描线程；以账号归属人身份传播会话上下文到异步线程
        User accountOwner = new User();
        accountOwner.setUserId(userId);
        accountOwner.setUserName("system-sync");
        CompletableFuture.runAsync(SecurityContextRunnable.wrap(() -> {
            try {
                syncAccountSafely(accountId);
            } catch (Exception e) {
                log.error("邮箱账号 {} 异步同步失败", accountId, e);
            }
        }, accountOwner, new Tenant(tenantCode)));
    }

    private void syncAccountSafely(String accountId) {
        MailAccount account = accountService.getOwned(accountId);
        if (account == null) {
            return;
        }
        String password;
        try {
            password = accountService.decryptPassword(account);
        } catch (IllegalStateException e) {
            // 凭据解密失败（KEK 未配置/密文损坏）— 无明文密码无法同步，仅记日志
            log.warn("邮箱账号 {} 凭据不可用，跳过定时同步: {}", accountId, e.getMessage());
            return;
        }
        accountService.markSyncRunning(accountId);
        try {
            MailSyncService.SyncResult result = mailSyncService.syncAccount(account, password);
            accountService.markSyncResult(accountId, true);
            log.info("邮箱账号 {} 定时同步完成: synced={}, total={}", accountId, result.synced(), result.total());
        } catch (MessagingException e) {
            accountService.markSyncResult(accountId, false);
            log.warn("邮箱账号 {} 定时同步失败: {}", accountId, e.getMessage());
        } catch (Exception e) {
            accountService.markSyncResult(accountId, false);
            log.error("邮箱账号 {} 定时同步异常", accountId, e);
        }
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
