package cn.geelato.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 安全数据快照刷新协调器：组织/用户/角色三快照的统一装载与级联刷新。
 * User/Role Provider 可缺席（直连式实现自行管理数据面时）。
 */
@Slf4j
public class SecurityDataRefreshCoordinator {
    private final OrgProvider orgProvider;
    private final UserProvider userProvider;
    private final RoleProvider roleProvider;

    public SecurityDataRefreshCoordinator(OrgProvider orgProvider, @Autowired(required = false) UserProvider userProvider) {
        this(orgProvider, userProvider, null);
    }

    public SecurityDataRefreshCoordinator(OrgProvider orgProvider,
                                          @Autowired(required = false) UserProvider userProvider,
                                          @Autowired(required = false) RoleProvider roleProvider) {
        this.orgProvider = orgProvider;
        this.userProvider = userProvider;
        this.roleProvider = roleProvider;
    }

    @PostConstruct
    public void init() {
        refreshAll();
    }

    public synchronized void refreshAll() {
        orgProvider.refresh();
        if (userProvider != null) {
            userProvider.refresh();
        }
        refreshRoleSnapshot();
        log.info("Security provider snapshots refreshed. userProviderLoaded={}, roleProviderLoaded={}",
                userProvider != null, roleProvider != null);
    }

    public synchronized void refreshOrg() {
        orgProvider.refresh();
        if (userProvider != null) {
            userProvider.refresh();
        }
        log.info("Org provider snapshot refreshed with user snapshot cascade. userProviderLoaded={}", userProvider != null);
    }

    public synchronized void refreshUser() {
        if (userProvider != null) {
            userProvider.refresh();
        }
        log.info("User provider snapshot refreshed. userProviderLoaded={}", userProvider != null);
    }

    public synchronized void refreshRole() {
        refreshRoleSnapshot();
        log.info("Role provider snapshot refreshed. roleProviderLoaded={}", roleProvider != null);
    }

    private void refreshRoleSnapshot() {
        if (roleProvider != null) {
            roleProvider.refresh();
        }
    }
}
