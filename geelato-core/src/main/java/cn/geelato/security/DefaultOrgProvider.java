package cn.geelato.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DefaultOrgProvider extends OrgProvider {
    private final JdbcTemplate platformJdbcTemplate;

    public DefaultOrgProvider(@Qualifier("primaryJdbcTemplate") JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        loadData(null);
    }

    @Override
    public void loadData(Object orgData) {
        List<Org> list = platformJdbcTemplate.query(
                "select * from platform_org where del_status = 0",
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
        for (Org o : list) {
            if (o.getOrgId() != null && !o.getOrgId().isEmpty()) {
                orgDataMap.put(o.getOrgId(), o);
            }
        }
    }
    private String getSafeString(java.sql.ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception e) {
            return null;
        }
    }
}
