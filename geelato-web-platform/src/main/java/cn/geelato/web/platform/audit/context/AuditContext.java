package cn.geelato.web.platform.audit.context;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求级审计上下文（ThreadLocal）。
 *
 * <p>职责：
 * <ol>
 *   <li><b>两层去重协调</b>：第1层（@AuditLog）声明业务动作后，第2层（ORM 兜底）对同 targetId 的写操作
 *       只挂明细不重复生成兜底记录，避免一次业务方法调用产生两条审计。</li>
 *   <li><b>通道A意图传递</b>：前端/低代码通过请求头 {@code X-Audit-Operation} 声明的业务意图写入此处，
 *       使 MQL/通用 CRUD 也能带上业务语义。</li>
 *   <li><b>代码内声明</b>：业务代码/Graal 脚本可调 {@link #declare} 显式声明意图。</li>
 * </ol>
 *
 * <p>由 {@code AuditContextFilter} 在请求开始时创建、结束时清理；非 HTTP 线程（如定时任务）首次访问时懒创建。
 */
@Getter
@Setter
public class AuditContext {

    private static final ThreadLocal<AuditContext> HOLDER = ThreadLocal.withInitial(AuditContext::new);

    /** 本次请求已声明业务动作的 targetId 集合（用于 ORM 兜底层去重判断）。 */
    private final Set<String> declaredTargetIds = ConcurrentHashMap.newKeySet();

    /** 通道A/代码声明的业务动作名（如"审批运单"）。 */
    private volatile String declaredOperName;

    /** 通道A/代码声明的业务类型（如"freight_order"）。 */
    private volatile String declaredBizType;

    /** 通道A/代码声明的目标ID（使 MQL 路径也能关联到具体业务对象）。 */
    private volatile String declaredTargetId;

    /** 当前 HTTP 请求ID（由过滤器注入，便于关联）。 */
    private volatile String requestId;

    public static AuditContext current() {
        return HOLDER.get();
    }

    /**
     * 声明一次业务动作。第1层注解切面与通道A拦截器、代码内 API 均调用此方法。
     * 调用后 ORM 兜底层会对同 targetId 的写操作降级处理。
     *
     * @param operName 业务动作名
     * @param bizType  业务类型
     * @param targetId 业务对象主键（可为空）
     */
    public void declare(String operName, String bizType, String targetId) {
        if (operName != null && !operName.isEmpty()) {
            this.declaredOperName = operName;
        }
        if (bizType != null && !bizType.isEmpty()) {
            this.declaredBizType = bizType;
        }
        if (targetId != null && !targetId.isEmpty()) {
            this.declaredTargetId = targetId;
            this.declaredTargetIds.add(targetId);
        }
    }

    /** ORM 兜底层判断：该 targetId 是否已被第1层声明（若是，则降级为明细，不重复生成兜底记录）。 */
    public boolean isDeclared(String targetId) {
        return targetId != null && !targetId.isEmpty() && declaredTargetIds.contains(targetId);
    }

    /** 清理当前线程上下文（请求结束时调用）。 */
    public static void clear() {
        AuditContext ctx = HOLDER.get();
        ctx.declaredTargetIds.clear();
        ctx.declaredOperName = null;
        ctx.declaredBizType = null;
        ctx.declaredTargetId = null;
        ctx.requestId = null;
        HOLDER.remove();
    }

    /** 判断当前线程是否已有上下文（用于避免不必要的 ThreadLocal 创建）。 */
    public static boolean exists() {
        return HOLDER.get() != null;
    }
}
