package cn.geelato.core.env;


import cn.geelato.core.AbstractManager;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.security.*;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境与安全数据的内存缓存管理器。
 *
 * <p>本类位于框架层（geelato-core），只负责内存缓存与对外 API；
 * 所有对 platform_* 表的访问都委托给 {@link EnvStore} SPI 实现（由业务层 geelato-web-platform 提供）。
 * 当未注入 {@link EnvStore} 时（框架独立运行），加载动作跳过，对外读 API 返回空结果。</p>
 */
@Slf4j
public class EnvManager  extends AbstractManager {
    // 内存缓存相关
    private static final long USER_CACHE_EXPIRE_MILLIS = 30 * 60 * 1000; // 30分钟
    private final Map<String, CachedUser> userCache = new ConcurrentHashMap<>();

    private final Map<String ,Map<String , SysConfig>> sysConfigClassifyMap;
    private final Map<String ,SysConfig> sysConfigMap;
    /**
     * 平台数据加载 SPI，由业务层注入。
     */
    @Setter
    private EnvStore envStore;
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
        if (envStore == null) {
            log.info("LoadSysConfig skipped (no EnvStore provided)");
            return;
        }
        List<SysConfig> sysConfigList = envStore.loadAllSysConfig();
        if (sysConfigList == null) {
            return;
        }
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
        if (envStore == null) {
            log.info("refreshConfig skipped (no EnvStore provided)");
            return;
        }
        SysConfig sysConfig = envStore.loadSysConfig(configKey);
        if(sysConfig!=null){
            String key=sysConfig.getConfigKey();
            String purpose=sysConfig.getPurpose();
            if(sysConfigMap.containsKey(key)){
                sysConfigMap.replace(key,sysConfig);
            }else{
                sysConfigMap.put(key,sysConfig);
            }
            Map<String, SysConfig> purposeMap = sysConfigClassifyMap.computeIfAbsent(purpose, k -> new HashMap<>());
            if(purposeMap.containsKey(key)){
                purposeMap.replace(key,sysConfig);
            }else {
                purposeMap.put(key,sysConfig);
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
        if (envStore == null) {
            log.info("InitCurrentUser skipped (no EnvStore provided)");
            return null;
        }
        String cacheKey = loginName + ":" + tenantCode;
        CachedUser cachedUser = userCache.get(cacheKey);
        if (cachedUser != null && !cachedUser.isExpired()) {
            log.debug("从缓存中获取用户信息: {}", loginName);
            User cachedUserData = cachedUser.getUser();
            // 权限信息每次都需要从数据库重新加载
            loadUserPermission(cachedUserData);
            return cachedUserData;
        }
        log.debug("从数据源查询用户信息: {}", loginName);
        User user = envStore.loadUser(loginName, tenantCode);

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

            loadUserOrg(user);
            loadTenant(user);
            loadUserRole(user);

            // 将用户基本信息放入缓存（不包含权限信息）
            userCache.put(cacheKey, new CachedUser(user));
            log.debug("用户信息已缓存: {}", loginName);

            // 权限信息每次都需要从数据库重新加载
            loadUserPermission(user);
        }
        return user;
    }

    private void loadUserRole(User user) {
        List<UserRole> userRoles = envStore.loadUserRoles(user.getUserId());
        if (userRoles == null) {
            userRoles = new ArrayList<>();
        }
        user.setUserRoles(userRoles);
    }

    private void loadUserOrg(User user) {
        List<UserOrg> userOrgs = envStore.loadUserOrgs(user.getUserId());
        if (userOrgs == null) {
            userOrgs = new ArrayList<>();
        }
        user.setUserOrgs(userOrgs);
        UserOrg defaultOrg = userOrgs.stream()
                .filter(org -> Boolean.TRUE.equals(org.getDefaultOrg()))
                .findFirst()
                .orElse(null);
        user.setDefaultOrg(defaultOrg);
        user.setExtendId(defaultOrg == null ? null : defaultOrg.getExtendId());
    }
    private void loadTenant(User user) {
        user.setTenant(new Tenant(user.getTenantCode()));
    }

    private void loadUserPermission(User user) {
        user.setDataPermissions(structDataPermission(user.getUserId()));
        user.setElementPermissions(structElementPermission(user.getUserId()));
    }



    private List<Permission> structDataPermission(String userId) {
        List<Permission> rolePermission = envStore.loadDataPermissions(userId);
        if (rolePermission == null) {
            return new ArrayList<>();
        }
        return rolePermission;
    }


    private List<Permission> structElementPermission(String userId) {
        if (envStore == null) {
            return new ArrayList<>();
        }
        List<Permission> elementPermissions = envStore.loadElementPermissions(userId);
        return elementPermissions == null ? new ArrayList<>() : elementPermissions;
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
