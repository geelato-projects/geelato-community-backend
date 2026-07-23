package cn.geelato.web.platform.srv.weixin.service;

import cn.geelato.meta.User;
import cn.geelato.orm.MetaFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WeixinWorkService {

    public String getCompanyWeixinWorkInfo(String orgId) {
        Map<String, Object> row = MetaFactory.sql("SELECT weixin_work_info FROM platform_company WHERE org_id = ?")
                .param(orgId)
                .one();
        return row == null ? null : Optional.ofNullable(row.get("weixin_work_info")).map(Object::toString).orElse(null);
    }

    public void updateUserWeixinWorkUserId(String weixinWorkUserId, String userId) {
        MetaFactory.sql("UPDATE platform_user SET weixin_work_userId = ? WHERE id = ?")
                .params(weixinWorkUserId, userId)
                .execute();
    }

    public List<User> findUsersByOrgId(String orgId) {
        return MetaFactory.sql("""
                        WITH RECURSIVE org_tree AS (
                          SELECT id FROM platform_org WHERE id = ?
                          UNION ALL
                          SELECT o.id FROM platform_org o JOIN org_tree ot ON o.pid = ot.id
                        )
                        SELECT u.* FROM platform_user u WHERE u.org_id IN (SELECT id FROM org_tree) 
                        AND u.del_status = 0 AND u.enable_status = 1
                        """)
                .param(orgId)
                .wrapperResult(this::toUser)
                .list();
    }

    public String getWeixinWorkUserIdByUserId(String userId) {
        Map<String, Object> row = MetaFactory.sql("SELECT weixin_work_userId FROM platform_user WHERE id = ?")
                .param(userId)
                .one();
        return row == null ? null : Optional.ofNullable(row.get("weixin_work_userId")).map(Object::toString).orElse(null);
    }

    public String getWeixinConfig(String tenantId) {
        Map<String, Object> row = MetaFactory.sql("SELECT weixin_config FROM platform_tenant WHERE id = ?")
                .param(tenantId)
                .one();
        return row == null ? null : Optional.ofNullable(row.get("weixin_config")).map(Object::toString).orElse(null);
    }

    public int updateWeixinConfig(String tenantId, String weixinConfig) {
        return MetaFactory.sql("UPDATE platform_tenant SET weixin_config = ? WHERE id = ?")
                .params(weixinConfig, tenantId)
                .execute();
    }

    private User toUser(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        User user = new User();
        user.setId(Optional.ofNullable(row.get("id")).map(Object::toString).orElse(null));
        user.setMobilePhone(Optional.ofNullable(row.get("mobile_phone")).map(Object::toString).orElse(null));
        user.setWeixinWorkUserId(Optional.ofNullable(row.get("weixin_work_userId")).map(Object::toString).orElse(null));
        return user;
    }
}
