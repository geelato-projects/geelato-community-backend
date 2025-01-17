package cn.geelato.core.env;


import cn.geelato.core.AbstractManager;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.env.entity.Permission;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.core.env.entity.User;
import cn.geelato.core.env.entity.UserMenu;
import cn.geelato.core.orm.Dao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class EnvManager  extends AbstractManager {
    private final Map<String ,Map<String , SysConfig>> sysConfigClassifyMap;
    private final Map<String ,SysConfig> sysConfigMap;
    private Dao  EnvDao;
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
    public void SetDao(Dao dao){
        this.EnvDao=dao;
    }
    public  void EnvInit(){
        LoadSysConfig();
    }

    private void LoadSysConfig() {
        String sql = "select config_key as configKey,config_value as configValue,app_Id as appId,tenant_code as tenantCode,purpose as purpose " +
                "from platform_sys_config where enable_status =1 and del_status =0";
        List<SysConfig> sysConfigList = EnvDao.getJdbcTemplate().query(sql,new BeanPropertyRowMapper<>(SysConfig.class));
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
        SysConfig sysConfig = EnvDao.getJdbcTemplate().queryForObject(String.format(sql,configKey),
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
    public User InitCurrentUser(String loginName) {
        String sql = "select id as userId,org_id as defaultOrgId,login_name as loginName," +
                "name as userName,bu_id as buId,dept_id as deptId,union_id as unionId,"+
                " cooperating_org_id as cooperatingOrgId from platform_user  where login_name =?";
        User dbUser = EnvDao.getJdbcTemplate().queryForObject(sql,new BeanPropertyRowMapper<User>(User.class),new Object[]{loginName});
        dbUser.setMenus(StructUserMenu(dbUser.getUserId()));
        dbUser.setDataPermissions(StructDataPermission(dbUser.getUserId()));
        dbUser.setElementPermissions(StructElementPermission(dbUser.getUserId()));
        SessionCtx.setCurrentUser(dbUser);
        SessionCtx.setCurrentTenant("geelato");
        return dbUser;
    }

    private List<Permission> StructDataPermission(String userId) {
        String sql = "select t2.`object`  as entity,t2.rule as rule,t3.weight as role_weight from platform_role_r_permission t1 \n" +
                "left join platform_permission t2 on t1.permission_id =t2.id \n" +
                "left join platform_role t3 on t1.role_id =t3.id \n" +
                "left join platform_role_r_user t4 on t4.role_id =t3.id \n" +
                "left join platform_user t5 on t5.id =t4.user_id \n" +
                "where  t2.type='dp' and t1.del_status=0 and t2.del_status=0 and t3.del_status=0 and t4.del_status=0 and t5.id =?";
        return EnvDao.getJdbcTemplate().query(sql,
                new BeanPropertyRowMapper<>(Permission.class),new Object[]{userId});
    }

    private List<Permission> StructElementPermission(String userId) {
        return new ArrayList<>();
    }


    private List<UserMenu> StructUserMenu(String userId) {
        List<UserMenu> userMenuList=new ArrayList<>();

        UserMenu um1=new UserMenu();
        um1.setMenuUrl("");
        userMenuList.add(um1);

        return userMenuList;
    }
}
