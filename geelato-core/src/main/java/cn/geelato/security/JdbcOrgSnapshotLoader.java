package cn.geelato.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于平台组织表的默认组织快照加载器。
 */
public class JdbcOrgSnapshotLoader implements OrgSnapshotLoader {
    private final JdbcTemplate platformJdbcTemplate;

    public JdbcOrgSnapshotLoader(JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
    }

    @Override
    public OrgSnapshot load() {
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
