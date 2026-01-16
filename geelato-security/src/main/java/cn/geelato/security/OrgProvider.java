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

    protected Map<String, Org> orgDataMap = new ConcurrentHashMap<>();

    public String getOrgName(String orgId){
        Org org = orgDataMap.get(orgId);
        return org == null ? "" : org.getName();
    }

    public String getDeptId(String orgId){
        Org org = orgDataMap.get(orgId);
        if(org == null){
            return "";
        }
        String type = org.getType();
        if(type != null){
            String t = type.toLowerCase();
            if("department".equals(t)){
                return org.getOrgId();
            }
        }
        String currentId = org.getOrgId();
        String pid = org.getPid();
        while(pid != null && !pid.isEmpty()){
            Org parent = orgDataMap.get(pid);
            if(parent == null){
                break;
            }
            String pt = parent.getType();
            if(pt != null){
                String ppt = pt.toLowerCase();
                if("department".equals(ppt)){
                    return parent.getOrgId();
                }
            }
            currentId = parent.getOrgId();
            pid = parent.getPid();
        }
        return currentId;
    }
    public String getCompanyId(String orgId){
        Org org = orgDataMap.get(orgId);
        if(org == null){
            return "";
        }
        if(org.getCompanyId()!=null && !org.getCompanyId().isEmpty()){
            return org.getCompanyId();
        }
        if(org.getType()!=null && org.getType().equalsIgnoreCase("company")){
            org.setCompanyId(org.getOrgId());
            orgDataMap.put(org.getOrgId(), org);
            return org.getCompanyId();
        }
        String currentId = org.getOrgId();
        String pid = org.getPid();
        while(pid != null && !pid.isEmpty()){
            Org parent = orgDataMap.get(pid);
            if(parent == null){
                break;
            }
            if(parent.getType()!=null && parent.getType().equalsIgnoreCase("company")){
                org.setCompanyId(parent.getOrgId());
                orgDataMap.put(org.getOrgId(), org);
                return org.getCompanyId();
            }
            currentId = parent.getOrgId();
            pid = parent.getPid();
        }
        return currentId;
    }

    public String getBuId(String orgId){
        return getCompanyId(orgId);
    }
    public abstract void loadData(Object orgData);
}
