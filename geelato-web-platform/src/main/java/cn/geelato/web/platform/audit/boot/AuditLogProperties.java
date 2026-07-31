package cn.geelato.web.platform.audit.boot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 审计日志配置。
 *
 * <p>按方案约定：只有 {@link #enabled} 一个总开关，控制是否启用审计日志（同时控制第1层注解与第2层 ORM 兜底）。
 * 其余字段均为非开关的内部调参项，提供合理默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "geelato.platform.audit")
public class AuditLogProperties {

    /** 唯一总开关：是否启用审计日志。关闭后第1层注解与第2层 ORM 兜底均不工作。 */
    private boolean enabled = true;

    // ===== ORM 兜底层调参（不可关停，仅作范围控制，避免噪音） =====

    /** ORM 兜底层排除的表名清单（这些表的写操作不记审计）。 */
    private List<String> ormFallbackExcludeTables = Arrays.asList(
            "platform_audit_log", "audit_event",
            "platform_dict", "platform_dict_item",
            "platform_dev_column", "platform_dev_table", "platform_dev_table_check",
            "platform_dev_table_foreign", "platform_dev_view", "platform_dev_db_connect");

    // ===== 业务编号识别 =====

    /** 业务编号候选列（按顺序匹配，取第一个非空值作为 target_name）。 */
    private List<String> bizNameColumns = Arrays.asList("orderNo", "billNo", "contractNo", "no", "name", "title");

    // ===== 脱敏 =====

    /** 需脱敏的字段名（包含匹配，忽略大小写）。 */
    private List<String> maskFields = Arrays.asList("password", "pwd", "idCard", "bankCard", "mobile", "phone", "email");

    // ===== 异步落库 =====

    /** 异步落库线程池大小。 */
    private int storeThreadPoolSize = 4;
    /** 异步落库队列容量。 */
    private int storeQueueCapacity = 2000;
    /** 异步落库失败时是否降级写文件 audit.log。 */
    private boolean storeFailToFile = true;
    /** 关键审计（删除/审批等 operType）是否同步落库（牺牲性能换可靠性）。 */
    private boolean storeSyncForCritical = false;

    // ===== 明细 =====

    /** 字段级明细最大字段数（超出截断，避免超大 diff）。 */
    private int detailMaxFields = 50;
    /** 摘要中展示的变化字段数上限。 */
    private int summaryMaxFields = 5;

    // ===== 保留 =====

    /** 审计日志保留天数（供后续归档清理任务使用）。 */
    private int retentionDays = 730;

    /** 判断某表是否在 ORM 兜底排除清单内（大小写不敏感）。 */
    public boolean isExcludedTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        for (String t : ormFallbackExcludeTables) {
            if (t != null && t.equalsIgnoreCase(tableName)) {
                return true;
            }
        }
        return false;
    }
}
