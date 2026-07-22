package cn.geelato.core.env;

import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.security.Permission;
import cn.geelato.security.User;
import cn.geelato.security.UserOrg;
import cn.geelato.security.UserRole;

import java.util.List;

/**
 * 环境/安全数据的加载 SPI。
 *
 * <p>框架层（geelato-core）的 {@link EnvManager} 仅负责内存缓存与对外的同步访问 API，
 * 所有需要访问 platform_* 表的"加载"动作都委托给本接口的实现。</p>
 *
 * <p>默认实现位于业务层（geelato-web-platform 的 {@code cn.geelato.core.env.PlatformEnvStore}），
 * 框架层不绑定具体数据库表。当业务层未提供实现时（框架独立运行），{@link EnvManager} 的加载动作跳过。</p>
 */
public interface EnvStore {

    /**
     * 加载全部启用的系统配置。
     */
    List<SysConfig> loadAllSysConfig();

    /**
     * 按 key 加载单个系统配置（用于配置刷新）。
     */
    SysConfig loadSysConfig(String configKey);

    /**
     * 按登录名 + 租户编码加载用户基本信息（含启用状态、用户ID、租户编码等业务校验所需的字段）。
     *
     * @return 查询不到时返回 null
     */
    User loadUser(String loginName, String tenantCode);

    /**
     * 加载用户所属角色。
     */
    List<UserRole> loadUserRoles(String userId);

    /**
     * 加载用户所属组织（含递归组织树展开）。
     */
    List<UserOrg> loadUserOrgs(String userId);

    /**
     * 加载用户的数据权限集合。
     */
    List<Permission> loadDataPermissions(String userId);

    /**
     * 加载用户的元素权限集合。
     */
    List<Permission> loadElementPermissions(String userId);
}
