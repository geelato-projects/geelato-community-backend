package cn.geelato.exception.engine.scan;

import cn.geelato.exception.entity.FmsExceptionRule;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 规则扫描周期：显式 cron 优先，缺省按等级（P0=15min/P1=1h/P2=4h/P3=每日 9 点，其它=P1）。
 * 到期判定：next(lastRun) <= now；lastRun 缺失视为立即可跑；missed fire 只补跑一次
 * （本轮执行后 lastRun 更新为本轮 start_at）。
 */
public final class RuleCronCalculator {

    private static final String DEFAULT_P0 = "0 */15 * * * *";
    private static final String DEFAULT_P1 = "0 0 * * * *";
    private static final String DEFAULT_P2 = "0 0 */4 * * *";
    private static final String DEFAULT_P3 = "0 0 9 * * *";

    private RuleCronCalculator() {
    }

    public static String cronOf(FmsExceptionRule rule) {
        if (rule.getCronExpression() != null && !rule.getCronExpression().isBlank()) {
            return rule.getCronExpression().trim();
        }
        return switch (rule.getLevel() == null ? "" : rule.getLevel()) {
            case "P0" -> DEFAULT_P0;
            case "P2" -> DEFAULT_P2;
            case "P3" -> DEFAULT_P3;
            default -> DEFAULT_P1;
        };
    }

    public static boolean isDue(FmsExceptionRule rule, LocalDateTime lastRunAt, LocalDateTime now) {
        try {
            CronExpression cron = CronExpression.parse(cronOf(rule));
            LocalDateTime base = lastRunAt != null ? lastRunAt : LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime next = cron.next(base);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            return false;
        }
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Instant i) {
            return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        }
        return null;
    }
}
