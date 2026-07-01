package cn.geelato.security;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class DefaultOrgProvider implements OrgProvider {
    private final OrgSnapshotLoader orgSnapshotLoader;
    private final AtomicReference<OrgSnapshot> snapshotRef = new AtomicReference<>(OrgSnapshot.empty());
    private final AtomicReference<OrgRelationResolver> relationResolverRef =
            new AtomicReference<>(new OrgRelationResolver(OrgSnapshot.empty().getOrgById()));

    public DefaultOrgProvider(OrgSnapshotLoader orgSnapshotLoader) {
        this.orgSnapshotLoader = orgSnapshotLoader;
    }

    @Override
    public Org getOrg(String orgId) {
        return snapshotRef.get().getOrg(orgId);
    }

    @Override
    public String getDeptId(String orgId) {
        return relationResolverRef.get().resolveDeptId(orgId);
    }

    @Override
    public String getCompanyId(String orgId) {
        return relationResolverRef.get().resolveCompanyId(orgId);
    }

    @Override
    public void refresh() {
        OrgSnapshot snapshot = orgSnapshotLoader.load();
        snapshotRef.set(snapshot);
        relationResolverRef.set(new OrgRelationResolver(snapshot.getOrgById()));
        log.info("Org provider snapshot refreshed. orgCount={}", snapshot.getOrgById().size());
    }
}
