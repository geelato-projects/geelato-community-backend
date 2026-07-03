package cn.geelato.web.platform.srv.tenant.service;

import cn.geelato.meta.Tenant;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TenantOrmService {

    public List<Tenant> queryTenantList(String code, String companyName, String corpId) {
        List<Filter> filters = new ArrayList<>();
        if (StringUtils.hasText(code)) {
            filters.add(Filter.like("code", code));
        }
        if (StringUtils.hasText(companyName)) {
            filters.add(Filter.like("companyName", companyName));
        }
        if (StringUtils.hasText(corpId)) {
            filters.add(Filter.eq("corpId", corpId));
        }
        filters.add(Filter.eq("delStatus", 0));
        return MetaFactory.query(Tenant.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("createAt"))
                .wrapperResult(this::toTenant)
                .list();
    }

    public Tenant getById(String id) {
        return MetaFactory.query(Tenant.class)
                .where(Filter.eq("id", id))
                .wrapperResult(this::toTenant)
                .one();
    }

    public Tenant getByCode(String code) {
        return MetaFactory.query(Tenant.class)
                .where(Filter.eq("code", code), Filter.eq("delStatus", 0))
                .wrapperResult(this::toTenant)
                .one();
    }

    public String create(Tenant tenant) {
        String id = MetaFactory.insert(Tenant.class)
                .values(toValueMap(tenant))
                .save();
        tenant.setId(id);
        return id;
    }

    public String updateById(Tenant tenant) {
        return MetaFactory.update(Tenant.class)
                .values(toValueMap(tenant))
                .where(Filter.eq("id", tenant.getId()))
                .save();
    }

    public String touchById(String id) {
        return MetaFactory.update(Tenant.class)
                .where(Filter.eq("id", id))
                .save();
    }

    private Tenant toTenant(Map<String, Object> row) {
        return row == null || row.isEmpty() ? null : JSON.parseObject(JSON.toJSONString(row), Tenant.class);
    }

    private Map<String, Object> toValueMap(Tenant tenant) {
        Map<String, Object> valueMap = JSON.parseObject(JSON.toJSONString(tenant), Map.class);
        valueMap.values().removeIf(value -> value == null);
        return valueMap;
    }
}
