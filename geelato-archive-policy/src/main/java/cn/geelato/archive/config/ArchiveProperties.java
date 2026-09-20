package cn.geelato.archive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * 数据归档全局配置（geelato.archive.*）。
 * <p>按方案约定：无总开关——引擎与策略管理随模块装配即生效，但策略默认停用，
 * 无启用策略绝不动任何数据。此处仅保留必要的调参项与防呆门禁。</p>
 */
@Data
@ConfigurationProperties(prefix = "geelato.archive")
public class ArchiveProperties {

    /** 每日执行时刻（全局一份，调度器按此串行执行所有启用策略） */
    private LocalTime scheduleTime = LocalTime.of(2, 0);

    /**
     * 定时调度器是否随应用启动：默认 false（引擎日常关闭——不创建任何调度线程，仅手动触发可用）。
     * 需要定时归档时置 true，或运行时调用 POST /archive/scheduler/start 随时启动（重启后回到本配置默认）。
     */
    private boolean schedulerEnabled = false;

    /** 最小保留天数门禁：retention_days 低于此值拒绝启用（防误配把活跃数据搬走） */
    private int minRetentionDays = 30;

    /** 保护名单：这些表禁止配置归档（防呆硬失败，大小写不敏感） */
    private List<String> protectedTables = Arrays.asList(
            "platform_archive_policy", "platform_archive_run",
            "platform_user", "platform_role", "platform_org", "platform_tenant",
            "platform_dev_db_connect", "platform_tenant_site");

    /** 新建策略的默认批大小 */
    private int defaultBatchSize = 200;

    /** 新建策略的默认批间隔（毫秒） */
    private int defaultBatchIntervalMs = 200;

    /** 新建策略的默认单次运行行数上限（渐进式归档；0=不限） */
    private long defaultMaxRowsPerRun = 1_000_000L;

    /** 预置模板的默认保留天数（audit 模板取 AuditLogProperties.retentionDays 同款 730） */
    private int presetAuditRetentionDays = 730;

    /** LOCAL_FILE 模式临时目录；空 = ${java.io.tmpdir}/geelato-archive */
    private String tempDir;

    /** 启动时幂等建表（存在即跳过），对齐 MailSchemaInitializer 模式 */
    private boolean autoInitTables = true;

    /** 启动时装载预置策略模板（幂等：code 已存在则跳过，不覆盖用户改动） */
    private boolean autoLoadPresets = true;

    public boolean isProtectedTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return true;
        }
        for (String t : protectedTables) {
            if (t != null && t.equalsIgnoreCase(tableName.trim())) {
                return true;
            }
        }
        return false;
    }
}
