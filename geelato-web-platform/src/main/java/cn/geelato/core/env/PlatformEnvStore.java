package cn.geelato.core.env;

import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.security.Permission;
import cn.geelato.security.User;
import cn.geelato.security.UserOrg;
import cn.geelato.security.UserRole;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 platform_* 表的默认 {@link EnvStore} 实现。
 *
 * <p>本类位于业务层（geelato-web-platform），框架层（geelato-core）的 {@link EnvManager}
 * 仅保留 {@link EnvStore} SPI 接口。保留原 package（cn.geelato.core.env）以维持 import 一致性，
 * 由 {@code @ComponentScan(basePackages = {"cn.geelato"})} 发现。</p>
 *
 * <p>历史 SQL 注入修复：原 {@code refreshConfig} 使用 {@code String.format} 拼接 configKey，
 * 现统一改为参数化查询。</p>
 */
@Component
public class PlatformEnvStore implements EnvStore {

    private static final String SQL_SYS_CONFIG_ALL =
            "select config_key as configKey,config_value as configValue,app_Id as appId,tenant_code as tenantCode,purpose as purpose " +
                    "from platform_sys_config where enable_status =1 and del_status =0";
    private static final String SQL_SYS_CONFIG_ONE =
            "select config_key as configKey,config_value as configValue,app_Id as appId,tenant_code as tenantCode,purpose as purpose " +
                    "from platform_sys_config where enable_status =1 and del_status =0 and config_key=?";

    private static final String SQL_USER_BY_LOGIN =
            "select " +
                    "id as userId, " +
                    "name as userName, " +
                    "login_name as loginName, " +
                    "tenant_code as tenantCode, " +
                    "job_number as jobNumber, " +
                    "description, " +
                    "org_id as orgId, " +
                    "org_id as defaultOrgId, " +
                    "cooperating_org_id as cooperatingOrgId, " +
                    "bu_id as buId, " +
                    "dept_id as deptId, " +
                    "en_name as enName, " +
                    "sex, " +
                    "avatar, " +
                    "mobile_prefix as mobilePrefix, " +
                    "mobile_phone as mobilePhone, " +
                    "telephone, " +
                    "email, " +
                    "post, " +
                    "nation_code as nationCode, " +
                    "province_code as provinceCode, " +
                    "city_code as cityCode, " +
                    "address, " +
                    "type, " +
                    "source, " +
                    "enable_status, " +
                    "weixin_unionId as weixinUnionId, " +
                    "weixin_work_userId as weixinWorkUserId " +
                    "from platform_user " +
                    "where del_status = 0 and login_name =? and tenant_code =?";

    private static final String SQL_USER_ROLES =
            " select t3.id,t3.code,t3.`NAME`,t3.type,t3.tenant_code as tenantCode from platform_role_r_user t1 left join platform_user t2 on t1.user_id=t2.id\n" +
                    "left join platform_role t3 on t3.id=t1.role_id where t2.id= ?";

    private static final String SQL_USER_ORGS = """
                            WITH RECURSIVE platform_org_tree AS (
                            SELECT
                                o.id,o.pid,o.code,o.name,
                                o.name AS full_name,
                                o.type,o.category,o.tenant_code,
                                CASE WHEN o.type = 'department' THEN o.id ELSE NULL END AS dept_id,
                                CASE WHEN o.type = 'company' THEN o.id ELSE NULL END AS company_id,
                                CASE WHEN o.type = 'company' THEN o.extend_id ELSE NULL END AS company_extend_id
                            FROM platform_org o WHERE o.pid IS NULL AND o.status = 1 AND o.del_status = 0
                            UNION ALL
                            SELECT
                                o.id,o.pid,o.code,o.name,
                                CONCAT(ot.full_name, '/', o.name) AS full_name,
                                o.type,o.category,o.tenant_code,
                                CASE WHEN o.type = 'department' THEN o.id ELSE ot.dept_id END AS dept_id,
                                CASE WHEN o.type = 'company' THEN o.id ELSE ot.company_id END AS company_id,
                                COALESCE(CASE WHEN o.type = 'company' THEN o.extend_id END, ot.company_extend_id) AS company_extend_id
                            FROM platform_org o JOIN platform_org_tree ot ON o.pid = ot.id WHERE o.status = 1 AND o.del_status = 0
                        ) SELECT t2.id AS orgId, t2.code, t2.name,t2.full_name AS fullName, t2.pid,t2.tenant_code AS tenantCode,
                        t2.dept_id AS deptId,t2.company_id AS companyId,t2.company_extend_id AS extendId,t1.default_org AS defaultOrg,t2.type,t2.category
                        FROM platform_org_r_user t1 LEFT JOIN platform_org_tree t2 ON t1.org_id =t2.id WHERE t1.del_status = 0 AND t1.user_id= ?""";

    private static final String SQL_ROLE_DATA_PERMISSION = """
                select t2.`object` as entity,t2.name as `name`,t2.rule as rule,t2.seq_no as weight, t3.weight as role_weight from platform_role_r_permission t1\s
                left join platform_permission t2 on t1.permission_id =t2.id\s
                left join platform_role_r_user t4 on t4.role_id =t1.role_id\s
                left join platform_role t3 on t4.role_id =t3.id\s
                left join platform_user t5 on t5.id =t4.user_id\s
                where  t2.type='dp' and t1.del_status=0 and t2.del_status=0 and t3.del_status=0 and t3.enable_status = 1 and t4.del_status=0 and t5.id =?""";

    private static final String SQL_USER_DATA_PERMISSION = """
                select * from platform_user_r_permission t1\s
                left join platform_permission t2 on t1.permission_id=t2.id
                where t2.type='dp' and t1.del_status=0 and t2.del_status=0\s
                and t1.user_id=?""";

    private final JdbcTemplate jdbcTemplate;

    public PlatformEnvStore(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SysConfig> loadAllSysConfig() {
        return jdbcTemplate.query(SQL_SYS_CONFIG_ALL, new BeanPropertyRowMapper<>(SysConfig.class));
    }

    @Override
    public SysConfig loadSysConfig(String configKey) {
        List<SysConfig> list = jdbcTemplate.query(SQL_SYS_CONFIG_ONE,
                new BeanPropertyRowMapper<>(SysConfig.class), configKey);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public User loadUser(String loginName, String tenantCode) {
        List<User> list = jdbcTemplate.query(SQL_USER_BY_LOGIN,
                new BeanPropertyRowMapper<>(User.class), loginName, tenantCode);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<UserRole> loadUserRoles(String userId) {
        return jdbcTemplate.query(SQL_USER_ROLES,
                new BeanPropertyRowMapper<>(UserRole.class), userId);
    }

    @Override
    public List<UserOrg> loadUserOrgs(String userId) {
        return jdbcTemplate.query(SQL_USER_ORGS,
                new BeanPropertyRowMapper<>(UserOrg.class), userId);
    }

    @Override
    public List<Permission> loadDataPermissions(String userId) {
        List<Permission> rolePermission = jdbcTemplate.query(SQL_ROLE_DATA_PERMISSION,
                new BeanPropertyRowMapper<>(Permission.class), userId);
        List<Permission> userPermission = jdbcTemplate.query(SQL_USER_DATA_PERMISSION,
                new BeanPropertyRowMapper<>(Permission.class), userId);
        return new ArrayList<>() {{
            addAll(rolePermission);
            addAll(userPermission);
        }};
    }

    @Override
    public List<Permission> loadElementPermissions(String userId) {
        //todo 历史遗留未实现
        return new ArrayList<>();
    }
}
