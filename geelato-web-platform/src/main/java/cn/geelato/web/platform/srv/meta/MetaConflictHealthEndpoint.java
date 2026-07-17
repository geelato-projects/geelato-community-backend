package cn.geelato.web.platform.srv.meta;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.lang.monitor.HealthEndpoint;
import cn.geelato.lang.monitor.HealthStatus;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实体元数据冲突健康检查端点。
 * <p>
 * 检测 Java 类源实体与在线 DB 源实体是否存在同名冲突（即同一 entityName
 * 同时存在于 {@code @Entity(name=...)} 与 {@code platform_dev_table.entity_name}）。
 * 这类冲突会导致 CRUD 构造 SQL 时表名/字段集不一致。
 * </p>
 * <p>
 * 实现自 {@link HealthEndpoint}，会被 {@code HealthCheckService} 自动扫描并周期检查，
 * 通过监控端点（{@code /monitor/health}）暴露，便于运维/告警系统发现。
 * </p>
 *
 * @author geemeta
 */
@Component
public class MetaConflictHealthEndpoint implements HealthEndpoint {

    private final MetaManager metaManager = MetaManager.singleInstance();

    @Override
    public HealthStatus checkHealthStatus() {
        try {
            int count = metaManager.getConflictCount();
            HealthStatus status = new HealthStatus();
            status.setModule("meta-conflict");
            // 冲突数为0即健康；否则标记为 UNKNOWN（不阻断启动，但需关注）
            status.setStatus(count == 0 ? HealthStatus.Status.HEALTH : HealthStatus.Status.UNKNOWN);
            List<String> names = metaManager.getConflictingEntityNames();
            status.setDetails("conflictCount=" + count + ", entities=" + JSON.toJSONString(names));
            return status;
        } catch (Exception e) {
            HealthStatus status = new HealthStatus();
            status.setModule("meta-conflict");
            status.setStatus(HealthStatus.Status.ABNORMAL);
            status.setDetails("检查实体冲突失败: " + e.getMessage());
            return status;
        }
    }
}
