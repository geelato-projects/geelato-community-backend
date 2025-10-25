package cn.geelato.orm.handler;

import cn.geelato.lang.meta.Entity;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import org.springframework.util.StringUtils;

/**
 * 实体表名处理器
 * 通过读取@Entity注解来自动解析表名，避免在实体类上重复添加@TableName注解
 * 
 * @author geelato
 */
public class EntityTableNameHandler implements TableNameHandler {

    /**
     * 动态表名处理方法
     * 从实体类的@Entity注解中解析表名
     * 
     * @param sql SQL语句
     * @param tableName 原始表名（通常是实体类名）
     * @return 解析后的表名
     */
    @Override
    public String dynamicTableName(String sql, String tableName) {
        // 尝试通过类名获取对应的实体类
        try {
            // 在项目中搜索对应的实体类
            Class<?> entityClass = findEntityClassByTableName(tableName);
            if (entityClass != null) {
                Entity entity = entityClass.getAnnotation(Entity.class);
                if (entity != null) {
                    // 按照优先级获取表名：table() > name() > 类名
                    if (StringUtils.hasText(entity.table())) {
                        return entity.table();
                    } else if (StringUtils.hasText(entity.name())) {
                        return entity.name();
                    }
                }
            }
        } catch (Exception e) {
            // 如果解析失败，返回原始表名
            return tableName;
        }
        
        // 如果没有找到@Entity注解或注解值为空，返回原始表名
        return tableName;
    }

    /**
     * 根据表名查找对应的实体类
     * 这里需要根据项目的包结构来实现具体的查找逻辑
     * 
     * @param tableName 表名
     * @return 实体类，如果未找到则返回null
     */
    private Class<?> findEntityClassByTableName(String tableName) {
        // 定义可能的实体类包路径
        String[] packagePaths = {
            "cn.geelato.meta",
            "cn.geelato.web.platform.m.model",
            "cn.geelato.web.platform.srv.model"
        };
        
        // 将表名转换为可能的类名（首字母大写，去掉下划线）
        String className = convertTableNameToClassName(tableName);
        
        for (String packagePath : packagePaths) {
            try {
                String fullClassName = packagePath + "." + className;
                Class<?> clazz = Class.forName(fullClassName);
                // 检查是否有@Entity注解
                if (clazz.isAnnotationPresent(Entity.class)) {
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                // 继续尝试下一个包路径
            }
        }
        
        return null;
    }

    /**
     * 将表名转换为类名
     * 例如：platform_dict -> PlatformDict
     * 
     * @param tableName 表名
     * @return 类名
     */
    private String convertTableNameToClassName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return tableName;
        }
        
        StringBuilder className = new StringBuilder();
        String[] parts = tableName.split("_");
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                className.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    className.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return className.toString();
    }
}