package cn.geelato.core.env;


import cn.geelato.core.AbstractManager;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.core.orm.Dao;
import cn.geelato.security.Permission;
import cn.geelato.security.User;
import cn.geelato.security.UserMenu;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class EnvManager  extends AbstractManager {
    private final Map<String ,Map<String , SysConfig>> sysConfigClassifyMap;
    private final Map<String ,SysConfig> sysConfigMap;
    private JdbcTemplate EnvJdbcTemplate;
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
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate){
        this.EnvJdbcTemplate=jdbcTemplate;
    }
    public  void EnvInit(){
        LoadSysConfig();
    }

    private void LoadSysConfig() {
        String sql = "select config_key as configKey,config_value as configValue,app_Id as appId,tenant_code as tenantCode,purpose as purpose " +
                "from platform_sys_config where enable_status =1 and del_status =0";
        List<SysConfig> sysConfigList = EnvJdbcTemplate.query(sql,new BeanPropertyRowMapper<>(SysConfig.class));
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
        SysConfig sysConfig = EnvJdbcTemplate.queryForObject(String.format(sql,configKey),
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
        String sql = "select id as userId,org_id as orgId,org_id as defaultOrgId,login_name as loginName," +
                "name as userName,bu_id as buId,dept_id as deptId,weixin_unionId as weixinUnionId,"+
                " cooperating_org_id as cooperatingOrgId,tenant_code as tenantCode from platform_user  " +
                "where del_status = 0 and login_name =? and tenant_code =?";
        User dbUser = EnvJdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(User.class), loginName,tenantCode);
        if (dbUser != null) {
            dbUser.setDataPermissions(structDataPermission(dbUser.getUserId()));
            dbUser.setElementPermissions(structElementPermission(dbUser.getUserId()));
        }
        return dbUser;
    }

    private List<Permission> structDataPermission(String userId) {
        String sql = "select t2.`object` as entity,t2.name as `name`,t2.rule as rule,t2.seq_no as weight, t3.weight as role_weight from platform_role_r_permission t1 \n" +
                "left join platform_permission t2 on t1.permission_id =t2.id \n" +
                "left join platform_role_r_user t4 on t4.role_id =t1.role_id \n" +
                "left join platform_role t3 on t4.role_id =t3.id \n" +
                "left join platform_user t5 on t5.id =t4.user_id \n" +
                "where  t2.type='dp' and t1.del_status=0 and t2.del_status=0 and t3.del_status=0 and t3.enable_status = 1 and t4.del_status=0 and t5.id =?";
        return EnvJdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(Permission.class), userId);
    }


    private List<Permission> structElementPermission(String userId) {
        //todo
        return new ArrayList<>();
    }

}
