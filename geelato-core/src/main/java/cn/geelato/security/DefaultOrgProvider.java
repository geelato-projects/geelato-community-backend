package cn.geelato.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Primary
@Slf4j
public class DefaultOrgProvider implements OrgProvider {
    private final JdbcTemplate platformJdbcTemplate;
    private final AtomicReference<OrgSnapshot> snapshotRef = new AtomicReference<>(OrgSnapshot.empty());
    private final AtomicReference<OrgRelationResolver> relationResolverRef =
            new AtomicReference<>(new OrgRelationResolver(OrgSnapshot.empty().getOrgById()));

    public DefaultOrgProvider(@Qualifier("primaryJdbcTemplate") JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
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
        OrgSnapshot snapshot = loadSnapshot();
        snapshotRef.set(snapshot);
        relationResolverRef.set(new OrgRelationResolver(snapshot.getOrgById()));
        log.info("Org provider snapshot refreshed. orgCount={}", snapshot.getOrgById().size());
    }

    private OrgSnapshot loadSnapshot() {
        List<Org> list = platformJdbcTemplate.query(
                "select id, pid, name, tenant_code, type, code from platform_org where del_status = 0",
                (rs, rowNum) -> {
                    Org o = new Org();
                    o.setOrgId(rs.getString("id"));
                    o.setPid(rs.getString("pid"));
                    o.setName(rs.getString("name"));
                    o.setTenantCode(rs.getString("tenant_code"));
                    o.setType(getSafeString(rs, "type"));
                    o.setCode(getSafeString(rs, "code"));
                    return o;
                }
        );
        Map<String, Org> orgById = new LinkedHashMap<>();
        for (Org o : list) {
            if (o.getOrgId() != null && !o.getOrgId().isEmpty()) {
                orgById.put(o.getOrgId(), o);
            }
        }
        return OrgSnapshot.from(orgById);
    }

    private String getSafeString(java.sql.ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception e) {
            return null;
        }
    }
}
