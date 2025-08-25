package cn.geelato.orm.interceptor;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.security.Tenant;
import cn.geelato.utils.UIDGenerator;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Properties;

/**
 * MyBatis拦截器，用于自动处理BaseEntity的基础属性
 * 包括ID生成、创建时间、更新时间、创建人、更新人等
 */
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class BaseEntityInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        
        // 处理INSERT和UPDATE操作
        if (parameter instanceof BaseEntity) {
            BaseEntity entity = (BaseEntity) parameter;
            Date now = new Date();
            String currentUser = getCurrentUser();
            
            if (SqlCommandType.INSERT.equals(sqlCommandType)) {
                // 处理INSERT操作
                String currentTenant = getCurrentTenant();
                handleInsert(entity, now, currentUser, currentTenant);
            } else if (SqlCommandType.UPDATE.equals(sqlCommandType)) {
                // 处理UPDATE操作
                handleUpdate(entity, now, currentUser);
            }
        }
        
        return invocation.proceed();
    }
    
    /**
     * 处理INSERT操作的字段设置
     */
    private void handleInsert(BaseEntity entity, Date now, String currentUser, String currentTenant) {
        // 设置ID（如果为空）
        if (entity instanceof IdEntity) {
            IdEntity idEntity = (IdEntity) entity;
            if (!StringUtils.hasText(idEntity.getId())) {
                idEntity.setId(String.valueOf(UIDGenerator.generate()));
            }
        }
        
        // 设置创建时间
        if (entity.getCreateAt() == null) {
            entity.setCreateAt(now);
        }
        
        // 设置更新时间
        if (entity.getUpdateAt() == null) {
            entity.setUpdateAt(now);
        }
        
        // 设置创建人
        if (!StringUtils.hasText(entity.getCreator())) {
            entity.setCreator(currentUser);
        }
        
        // 设置更新人
        if (!StringUtils.hasText(entity.getUpdater())) {
            entity.setUpdater(currentUser);
        }
        
        // 设置删除状态
        entity.setDelStatus(0);
        
        // 设置租户代码
        if (!StringUtils.hasText(entity.getTenantCode())) {
            entity.setTenantCode(currentTenant);
        }
    }
    
    /**
     * 处理UPDATE操作的字段设置
     */
    private void handleUpdate(BaseEntity entity, Date now, String currentUser) {
        // 设置更新时间
        entity.setUpdateAt(now);
        
        // 设置更新人
        entity.setUpdater(currentUser);
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUser() {
        try {
            User user = SecurityContext.getCurrentUser();
            return user != null && StringUtils.hasText(user.getUserId()) ? user.getUserId() : "system";
        } catch (Exception e) {
            return "system";
        }
    }
    
    /**
     * 获取当前租户代码
     */
    private String getCurrentTenant() {
        try {
            Tenant tenant = SecurityContext.getCurrentTenant();
            return tenant != null && StringUtils.hasText(tenant.getCode()) ? tenant.getCode() : "default";
        } catch (Exception e) {
            return "default";
        }
    }
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
    
    @Override
    public void setProperties(Properties properties) {
        // 可以在这里设置拦截器的属性
    }
}