package cn.geelato.security;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认角色提供者：经 {@link RoleSnapshotLoader} 整体装载快照，原子替换，
 * 与 {@link DefaultOrgProvider}/{@link DefaultUserProvider} 同范式。
 */
@Slf4j
public class DefaultRoleProvider implements RoleProvider {

    private final RoleSnapshotLoader roleSnapshotLoader;
    private final AtomicReference<RoleSnapshot> snapshotRef = new AtomicReference<>(RoleSnapshot.empty());

    public DefaultRoleProvider(RoleSnapshotLoader roleSnapshotLoader) {
        this.roleSnapshotLoader = roleSnapshotLoader;
    }

    @Override
    public Role getRole(String roleId) {
        return snapshotRef.get().getRole(roleId);
    }

    @Override
    public Role getRoleByCode(String roleCode) {
        return snapshotRef.get().getRoleByCode(roleCode);
    }

    @Override
    public List<OrgRole> getOrgRoles(String orgId) {
        return snapshotRef.get().getOrgRoles(orgId);
    }

    @Override
    public void refresh() {
        RoleSnapshot snapshot = roleSnapshotLoader.load();
        snapshotRef.set(snapshot);
        log.info("Role provider snapshot refreshed.");
    }
}
