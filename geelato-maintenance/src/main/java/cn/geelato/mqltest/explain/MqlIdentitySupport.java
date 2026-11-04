package cn.geelato.mqltest.explain;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.EnvStore;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MQL Playground 模拟身份支持。
 * <p>
 * Playground 端点放行了鉴权拦截，所以请求进入时 SecurityContext 为空。
 * 为了让 {@code PlatformMqlQueryFilterInjector}（租户隔离 + 数据权限）照常工作、
 * 生成与生产一致的 SQL，需要在执行前显式加载指定用户并设置到 SecurityContext，
 * 执行后清理。
 * <p>
 * 用户加载复用 {@link EnvManager#InitCurrentUser(String, String)}（与登录链路一致，
 * 含 dataPermissions、userOrgs、tenant 等完整属性）。EnvManager 的加载委托给
 * {@link EnvStore} SPI（由宿主的 PlatformEnvStore 实现）。
 */
@Slf4j
public class MqlIdentitySupport {

    private final JdbcTemplate jdbcTemplate;
    private final OrgProvider orgProvider;
    private final EnvStore envStore;

    public MqlIdentitySupport(JdbcTemplate jdbcTemplate, OrgProvider orgProvider, EnvStore envStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.orgProvider = orgProvider;
        this.envStore = envStore;
    }

    /**
     * 在指定身份上下文中执行操作。
     * <p>
     * 若 loginName/tenantCode 为空，则不设置用户（保持空 SecurityContext）——此时
     * 注入器会因 getCurrentUser() 为 null 而无法注入数据权限。调用方应确保需要
     * 数据权限的场景传入有效身份。
     *
     * @param loginName  登录名（platform_user.login_name）
     * @param tenantCode 租户编码
     * @param action     要执行的操作
     * @return 操作返回值
     */
    public <T> T runAs(String loginName, String tenantCode, java.util.concurrent.Callable<T> action) throws Exception {
        User user = null;
        Tenant tenant = null;
        if (StringUtils.hasText(loginName) && StringUtils.hasText(tenantCode)) {
            user = loadUser(loginName, tenantCode);
            if (user == null) {
                // 明确告知身份加载失败，而非静默设 null（否则下游注入器拿到 null 用户会抛晦涩的 NPE）
                throw new IllegalStateException(
                        String.format("无法加载模拟身份：loginName=%s, tenantCode=%s（请检查 platform_user 表是否存在该启用用户）",
                                loginName, tenantCode));
            }
            if (orgProvider != null) {
                user.setupOrgInfo(orgProvider);
            }
            tenant = new Tenant(tenantCode);
        }
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(tenant);
        try {
            return action.call();
        } finally {
            SecurityContext.clear();
        }
    }

    /**
     * 加载完整 User 对象（复用 EnvManager 的登录链路加载逻辑）。
     * EnvManager 委托 EnvStore SPI 访问 platform_* 表。
     */
    private User loadUser(String loginName, String tenantCode) {
        try {
            if (envStore == null) {
                log.warn("模拟身份加载跳过：未提供 EnvStore 实现（宿主未引入 platform EnvStore）");
                return null;
            }
            // 确保 EnvManager 持有 EnvStore（与宿主启动初始化一致）
            EnvManager envManager = EnvManager.singleInstance();
            envManager.setEnvStore(envStore);
            User user = envManager.InitCurrentUser(loginName, tenantCode);
            if (user == null) {
                log.warn("模拟身份加载失败：用户不存在 loginName={}, tenantCode={}", loginName, tenantCode);
            }
            return user;
        } catch (Exception e) {
            log.warn("模拟身份加载异常 loginName={}, tenantCode={}: {}", loginName, tenantCode, e.getMessage());
            return null;
        }
    }

    /**
     * 列出可选身份（从 platform_user 查询启用的用户）。
     * 用于 Playground 页面的身份选择器。
     */
    public List<Map<String, Object>> listIdentities(String tenantCode) {
        String tenantCondition = StringUtils.hasText(tenantCode) ? " and tenant_code = ?" : "";
        String sql = "select login_name as loginName, name as userName, tenant_code as tenantCode " +
                "from platform_user where del_status = 0 and enable_status = 1" + tenantCondition +
                " order by tenant_code, login_name limit 100";
        try {
            if (StringUtils.hasText(tenantCode)) {
                return jdbcTemplate.queryForList(sql, tenantCode);
            }
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("列出身份失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 列出所有租户编码（从 platform_user 去重）。
     */
    public List<String> listTenantCodes() {
        try {
            return jdbcTemplate.queryForList(
                    "select distinct tenant_code from platform_user where del_status = 0 and tenant_code is not null order by tenant_code",
                    String.class);
        } catch (Exception e) {
            log.warn("列出租户失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
