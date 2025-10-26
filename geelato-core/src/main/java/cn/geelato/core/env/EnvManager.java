package cn.geelato.core.env;


import cn.geelato.core.AbstractManager;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.security.Permission;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

import cn.geelato.security.UserOrg;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class EnvManager  extends AbstractManager {
    // 内存缓存相关
    private static final long USER_CACHE_EXPIRE_MILLIS = 30 * 60 * 1000; // 30分钟
    private final Map<String, CachedUser> userCache = new ConcurrentHashMap<>();
    
    private final Map<String ,Map<String , SysConfig>> sysConfigClassifyMap;
    private final Map<String ,SysConfig> sysConfigMap;
    @Setter
    private JdbcTemplate jdbcTemplate;
    private static EnvManager instance;

    private EnvManager(){
        log.info("EnvManager Instancing...");
        sysConfigMap=new HashMap<>();
        sysConfigClassifyMap=new HashMap<>();
    }


    public static EnvManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new EnvManager();
        }
        lock.unlock();
        return instance;
    }

    public  void EnvInit(){
        LoadSysConfig();
    }

    private void LoadSysConfig() {
        String sql = "select config_key as configKey,config_value as configValue,app_Id as appId,tenant_code as tenantCode,purpose as purpose " +
                "from platform_sys_config where enable_status =1 and del_status =0";
        List<SysConfig> sysConfigList = jdbcTemplate.query(sql,new BeanPropertyRowMapper<>(SysConfig.class));
        for (SysConfig config:sysConfigList) {
            if(!sysConfigMap.containsKey(config.getConfigKey())){
                sysConfigMap.put(config.getConfigKey(),config);
            }
            if(sysConfigClassifyMap.containsKey(config.getPurpose())){
                sysConfigClassifyMap.get(config.getPurpose()).put(config.getConfigKey(),config);
            }else{
                Map<String,SysConfig> map=new HashMap<>();
                map.put(config.getConfigKey(),config);
                sysConfigClassifyMap.put(config.getPurpose(),map);
            }
        }
    }


    public String getConfigValue(String configKey){
        if(this.sysConfigMap.containsKey(configKey)){
            return sysConfigMap.get(configKey).getConfigValue();
        }else{
            return "unable to find this config";
        }
    }

    public void refreshConfig(String configKey){
        String sql = "select config_key as configKey,config_value as configValue,app_Id as appId,tenant_code as tenantCode,purpose as purpose from platform_sys_config " +
                "where enable_status =1 and del_status =0 and config_key='%s'";
        SysConfig sysConfig = jdbcTemplate.queryForObject(String.format(sql,configKey),
                new BeanPropertyRowMapper<>(SysConfig.class));
        if(sysConfig!=null){
            String key=sysConfig.getConfigKey();
            String purpose=sysConfig.getPurpose();
            if(sysConfigMap.containsKey(key)){
                sysConfigMap.replace(key,sysConfig);
            }else{
                sysConfigMap.put(key,sysConfig);
            }
            if(sysConfigClassifyMap.get(purpose).containsKey(key)){
                sysConfigClassifyMap.get(purpose).replace(key,sysConfig);
            }else {
                sysConfigClassifyMap.get(purpose).put(key,sysConfig);
            }
        }
    }

    public Map<String ,SysConfig> getConfigMap(String purpose){
        return sysConfigClassifyMap.get(purpose);
    }
    public Map<String ,SysConfig> getAllConfig(){
        return sysConfigMap;
    }

    public List<Permission> getUserPermission(String userId, String entity){
        List<Permission> permissionList= structDataPermission(userId);
        return permissionList.stream().filter(x -> x.getEntity().equals(entity)).toList();
    }

    public User InitCurrentUser(String loginName,String tenantCode) {
        String cacheKey = loginName + ":" + tenantCode;
        CachedUser cachedUser = userCache.get(cacheKey);
        if (cachedUser != null && !cachedUser.isExpired()) {
            log.debug("从缓存中获取用户信息: {}", loginName);
            User cachedUserData = cachedUser.getUser();
            // 权限信息每次都需要从数据库重新加载
            loadUserPermission(cachedUserData);
            return cachedUserData;
        }
        log.debug("从数据库查询用户信息: {}", loginName);
        String sql = "select " +
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
        User user = jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(User.class), loginName, tenantCode);
        
        if (user != null) {
            if (user.getEnableStatus() == 0) {
                throw new RuntimeException("用户已被禁用");
            }
            if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
                throw new RuntimeException("用户ID不能为空");
            }
            if (user.getTenantCode() == null || user.getTenantCode().trim().isEmpty()) {
                throw new RuntimeException("租户编码不能为空");
            }
            if (user.getOrgId() == null || user.getOrgId().trim().isEmpty()) {
                throw new RuntimeException("组织ID不能为空");
            }
            if (user.getDefaultOrgId() == null || user.getDefaultOrgId().trim().isEmpty()) {
                throw new RuntimeException("默认组织ID不能为空");
            }
            
            loadUserOrg(user);
            loadTenant(user);
            
            // 将用户基本信息放入缓存（不包含权限信息）
            userCache.put(cacheKey, new CachedUser(user));
            log.debug("用户信息已缓存: {}", loginName);

            // 权限信息每次都需要从数据库重新加载
            loadUserPermission(user);
        }
        return user;
    }
    
    private void loadUserOrg(User user) {
        List<UserOrg> userOrgs=jdbcTemplate.query("    select " +
                "t2.id as orgId," +
                "t2.code," +
                "t2.name," +
                "t2.pid," +
                "t2.tenant_code as tenantCode," +
                "t2.bu_id as companyId," +
                "t2.extend_id as extendId," +
                "t1.default_org as defaultOrg," +
                "t2.type," +
                "t2.category from platform_org_r_user t1 left join platform_org t2 on t1.org_id =t2.id \n" +
                "where t2.`status`=1 and t2.del_status=0 and  t1.user_id=?",
                new BeanPropertyRowMapper<>(UserOrg.class), user.getUserId());
        user.setUserOrgs(userOrgs);
        user.setDefaultOrg(userOrgs.stream().filter(UserOrg::getDefaultOrg).findFirst().orElse(null));
    }
    private void loadTenant(User user) {
        user.setTenant(new Tenant(user.getTenantCode()));
    }

    private void loadUserPermission(User user) {
        user.setDataPermissions(structDataPermission(user.getUserId()));
        user.setElementPermissions(structElementPermission(user.getUserId()));
    }



    private List<Permission> structDataPermission(String userId) {
        String sql = "select t2.`object` as entity,t2.name as `name`,t2.rule as rule,t2.seq_no as weight, t3.weight as role_weight from platform_role_r_permission t1 \n" +
                "left join platform_permission t2 on t1.permission_id =t2.id \n" +
                "left join platform_role_r_user t4 on t4.role_id =t1.role_id \n" +
                "left join platform_role t3 on t4.role_id =t3.id \n" +
                "left join platform_user t5 on t5.id =t4.user_id \n" +
                "where  t2.type='dp' and t1.del_status=0 and t2.del_status=0 and t3.del_status=0 and t3.enable_status = 1 and t4.del_status=0 and t5.id =?";
        return jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(Permission.class), userId);
    }


    private List<Permission> structElementPermission(String userId) {
        //todo
        return new ArrayList<>();
    }

    /**
     * 清理过期的用户缓存
     */
    public void clearExpiredUserCache() {
        userCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("已清理过期的用户缓存");
    }

    /**
     * 清除指定用户的缓存
     * @param loginName 登录名
     * @param tenantCode 租户编码
     */
    public void clearUserCache(String loginName, String tenantCode) {
        String cacheKey = loginName + ":" + tenantCode;
        userCache.remove(cacheKey);
        log.debug("已清除用户缓存: {}", loginName);
    }

    /**
     * 清除所有用户缓存
     */
    public void clearAllUserCache() {
        userCache.clear();
        log.debug("已清除所有用户缓存");
    }

    /**
     * 获取当前缓存的用户数量
     * @return 缓存中的用户数量
     */
    public int getCachedUserCount() {
        return userCache.size();
    }

    /**
     * 缓存的用户数据包装类
     */
    private static class CachedUser {
        @Getter
        private final User user;
        private final long expireTime;

        public CachedUser(User user) {
            this.user = user;
            this.expireTime = System.currentTimeMillis() + USER_CACHE_EXPIRE_MILLIS;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

}
