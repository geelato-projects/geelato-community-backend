package cn.geelato.web.platform.graal.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.sql.SqlScriptParser;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.Digests;
import cn.geelato.utils.Encodes;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.utils.UUIDUtils;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.security.enums.*;
import cn.geelato.web.platform.utils.EncryptUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.script.ScriptException;
import javax.sql.DataSource;
import java.util.*;

@GraalService(name = "dao", built = "true")
public class GqlService extends RuleService {
    private final SqlScriptParser sqlScriptParser = new SqlScriptParser();
    private static final String EXECUTE_SQL_KEY = "execute_sql_key";

    public GqlService() {
        setDao(initDefaultDao(null));
    }

    private Dao initDefaultDao(String connectId) {
        DataSource ds = null;
        if (Strings.isNotBlank(connectId)) {
            ds = (DataSource) DataSourceManager.singleInstance().getLazyDataSource(connectId);
        } else {
            ds = (DataSource) DataSourceManager.singleInstance().getDynamicDataSourceMap().get("primary");
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }

    /**
     * 执行与给定SQL键关联的SQL语句，并返回执行结果。
     *
     * @param sqlKey    SQL键，用于标识要执行的SQL语句
     * @param connectId 连接ID，用于标识要使用的数据库连接
     * @param params    SQL语句中所需的参数，以键值对的形式提供
     * @return SQL语句的执行结果
     * @throws ScriptException       如果在执行SQL语句时发生脚本异常，则抛出此异常
     * @throws NoSuchMethodException 如果在尝试执行SQL语句时找不到对应的方法，则抛出此异常
     */
    public Object executeSqlKey(String sqlKey, String connectId, Map<String, Object> params) throws ScriptException, NoSuchMethodException {
        return this.initDefaultDao(connectId).executeKey(sqlKey, params);
    }

    /**
     * 查询字典项
     * <p>
     * 根据字典ID查询对应的字典项列表。
     *
     * @param dictId 字典ID
     * @return 返回查询结果的ApiResult对象，包含字典项列表
     */
    public ApiResult queryDictItems(String dictId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("dictId", dictId);
            params.put("enableStatus", 1);
            List<DictItem> list = this.initDefaultDao(null).queryList(DictItem.class, params, "seqNo asc");
            return ApiResult.success(list);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    public String afterCreateTenant(String tenantId) {
        Dao dao = this.initDefaultDao(null);
        // 查询租户信息
        List<Map<String, Object>> tenantList = dao.getJdbcTemplate().queryForList("select * from platform_tenant where id = ?", tenantId);
        if (tenantList.isEmpty()) {
            throw new RuntimeException("租户不存在");
        }
        Map<String, Object> tenantMap = tenantList.get(0);
        Map<String, Object> baseMap = new HashMap<>();
        baseMap.put("dept_id", tenantMap.get("dept_id"));
        baseMap.put("bu_id", tenantMap.get("bu_id"));
        baseMap.put("tenant_code", tenantMap.get("code"));
        baseMap.put("update_at", tenantMap.get("update_at"));
        baseMap.put("updater", tenantMap.get("updater"));
        baseMap.put("updater_name", tenantMap.get("updater_name"));
        baseMap.put("create_at", tenantMap.get("create_at"));
        baseMap.put("creator", tenantMap.get("creator"));
        baseMap.put("creator_name", tenantMap.get("creator_name"));
        // platform_tenant_site
        if (tenantMap.get("companyDomain") != null) {
            Map<String, Object> tenantSite = new HashMap<>();
            tenantSite.put("id", UIDGenerator.generate());
            tenantSite.put("name", tenantMap.get("company_name"));
            tenantSite.put("lang", "cn");
            tenantSite.put("domain", tenantMap.get("companyDomain"));
            tenantSite.putAll(baseMap);
            dao.execute("tenant_platform_tenant_site", tenantSite);
        }
        // platform_org
        Map<String, Object> org = new HashMap<>();
        String orgId = String.valueOf(UIDGenerator.generate());
        String orgName = Objects.toString(tenantMap.get("company_name"), "");
        org.put("id", orgId);
        org.put("name", orgName);
        org.put("code", UUIDUtils.generateRandom(13));
        org.put("type", OrgTypeEnum.COMPANY.getValue());
        org.put("category", OrgCategoryEnum.INSIDE.getValue());
        org.put("seq_no", ColumnDefault.SEQ_NO_FIRST);
        org.putAll(baseMap);
        dao.execute("tenant_platform_org", baseMap);
        // platform_user
        Map<String, Object> user = new HashMap<>();
        String userId = String.valueOf(UIDGenerator.generate());
        String userName = "管理员";
        String plainPassword = RandomStringUtils.randomAlphanumeric(EncryptUtil.SALT_SIZE);
        String salt = Encodes.encodeHex(Digests.generateSalt(EncryptUtil.SALT_SIZE));
        user.put("id", userId);
        user.put("name", userName);
        user.put("org_id", orgId);
        user.put("orgName", orgName);
        user.put("login_name", String.format("%s_%s", tenantMap.get("code"), "admin"));
        user.put("source", UserSourceEnum.LOCAL_USER.getValue());
        user.put("email", tenantMap.get("main_email"));
        user.put("type", UserTypeEnum.SYSTEM.getValue());
        user.put("sex", UserSexEnum.FEMALE.getValue());
        user.put("salt", salt);
        user.put("password", Encodes.encodeHex(Digests.sha1(plainPassword.getBytes(), salt.getBytes(), EncryptUtil.HASH_ITERATIONS)));
        user.put("enable_status", ColumnDefault.ENABLE_STATUS_VALUE);
        user.put("seq_no", ColumnDefault.SEQ_NO_FIRST);
        user.putAll(baseMap);
        dao.execute("tenant_platform_user", user);
        // platform_org_r_user
        Map<String, Object> orgUser = new HashMap<>();
        orgUser.put("id", UIDGenerator.generate());
        orgUser.put("org_id", orgId);
        orgUser.put("org_name", orgName);
        orgUser.put("user_id", userId);
        orgUser.put("user_name", userName);
        orgUser.put("default_org", IsDefaultOrgEnum.IS.getValue());
        orgUser.putAll(baseMap);
        dao.execute("tenant_platform_org_r_user", user);
        // platform_role
        Map<String, Object> role = new HashMap<>();
        String roleId = String.valueOf(UIDGenerator.generate());
        String roleName = "平台维护员";
        role.put("id", roleId);
        role.put("name", roleName);
        role.put("code", "super_admin");
        role.put("type", RoleTypeEnum.PLATFORM.getValue());
        role.put("weight", 0);
        role.put("enable_status", ColumnDefault.ENABLE_STATUS_VALUE);
        role.put("seq_no", ColumnDefault.SEQ_NO_FIRST);
        role.put("used_app", 1);
        role.putAll(baseMap);
        dao.execute("tenant_platform_role", user);
        // platform_role_r_user
        Map<String, Object> roleUser = new HashMap<>();
        roleUser.put("id", UIDGenerator.generate());
        roleUser.put("role_id", roleId);
        roleUser.put("role_name", roleName);
        roleUser.put("user_id", userId);
        roleUser.put("user_name", userName);
        roleUser.putAll(baseMap);
        dao.execute("tenant_platform_role_r_user", user);
        return plainPassword;
    }

    /**
     * js函数,将sql内容转换为js函数
     *
     * @param encodingContent 编码内容
     */
    private String javaScriptFunction(String encodingContent) {
        List<String> lines = new ArrayList<>();
        lines.add("-- @sql " + EXECUTE_SQL_KEY);
        lines.add(encodingContent);
        Map<String, String> map = sqlScriptParser.parse(lines);
        if (map == null || !map.containsKey(EXECUTE_SQL_KEY)) {
            throw new RuntimeException("encodingContent to javaScript Function error");
        }
        return map.get(EXECUTE_SQL_KEY);
    }

    private Map<String, Object> mixParam(Map<String, Object> paramMap) {
        if (paramMap == null || paramMap.isEmpty()) {
            paramMap = new HashMap<>();
        }
        return paramMap;
    }
}
