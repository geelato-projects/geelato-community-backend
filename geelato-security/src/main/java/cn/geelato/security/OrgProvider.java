package cn.geelato.security;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组织信息提供者抽象类
 * 用于提供组织相关的信息查询功能
 */
@Getter
public abstract class OrgProvider {
    
    /**
     * 组织数据缓存
     * -- GETTER --
     *  获取组织数据
     *
     */
    protected Map<String, Object> orgDataMap = new ConcurrentHashMap<>();
    
    /**
     * 根据组织ID获取组织名称
     * 
     * @param orgId 组织ID
     * @return 组织名称
     */
    public abstract String getOrgName(String orgId);
    
    /**
     * 根据组织ID获取部门ID
     * 
     * @param orgId 组织ID
     * @return 部门ID
     */
    public abstract String getDeptId(String orgId);
    
    /**
     * 根据组织ID获取公司ID
     * 
     * @param orgId 组织ID
     * @return 公司ID
     */
    public abstract String getCompanyId(String orgId);
    
    /**
     * 加载组织数据
     * 将数据加载到orgDataMap中
     * 
     * @param dataMap 组织数据
     */
    public abstract void loadOrgData(Map<String, Object> dataMap);

}
