package cn.geelato.datasource;

import cn.geelato.datasource.annotion.UseDynamicDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * 动态Dao字段处理器
 * 处理被@UseDynamicDataSource注解标记的字段，自动寻找对应的dynamicDao实例进行注入
 */
@Component
@Slf4j
public class DynamicDaoFieldProcessor implements BeanPostProcessor, ApplicationContextAware {
        
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public Object postProcessBeforeInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        return bean;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, @NotNull String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        ReflectionUtils.doWithFields(clazz, field -> {
            UseDynamicDataSource annotation = field.getAnnotation(UseDynamicDataSource.class);
            if (annotation != null) {
                processDynamicDaoField(bean, field, annotation);
            }
        });
        
        return bean;
    }
    
    /**
     * 处理被@UseDynamicDataSource注解标记的字段
     */
    private void processDynamicDaoField(Object bean, Field field, UseDynamicDataSource annotation) {
        try {
            String dataSourceKey = annotation.value();
            Class<?> fieldType = field.getType();
            String dynamicBeanName = "dynamic" + fieldType.getSimpleName();
            log.debug("operate field {}.{}, datasource: {}, found Bean: {}",
                    bean.getClass().getSimpleName(), field.getName(), dataSourceKey, dynamicBeanName);
            Object dynamicDao;
            try {
                dynamicDao = applicationContext.getBean(dynamicBeanName, fieldType);
                log.debug("found dynamic bean: {}", dynamicBeanName);
            } catch (Exception e) {
                log.error("unfound dynamic bean: {}。filed: {}.{}",
                        dynamicBeanName, bean.getClass().getSimpleName(), field.getName());
                throw new RuntimeException(String.format(
                        "@UseDynamicDataSource注解要求使用dynamic版本的Bean，但未找到Bean: %s。" +
                        "请在配置类中定义@Bean方法创建%s实例。", 
                        dynamicBeanName, dynamicBeanName), e);
            }
            field.setAccessible(true);
            field.set(bean, dynamicDao);
            log.info("inject dynamic dao to field {}.{}, datasource: {}",
                    bean.getClass().getSimpleName(), field.getName(), dataSourceKey);
            setDataSourceContext(dynamicDao, dataSourceKey, annotation);
        } catch (Exception e) {
            log.error("inject dynamic dao fail: {}.{}",
                    bean.getClass().getSimpleName(), field.getName(), e);
        }
    }
    
    /**
     * 设置数据源上下文信息
     */
    private void setDataSourceContext(Object dao, String dataSourceKey, UseDynamicDataSource annotation) {
        try {
            try {
                java.lang.reflect.Method setDataSourceMethod = dao.getClass().getMethod("setDataSourceKey", String.class);
                setDataSourceMethod.invoke(dao, dataSourceKey);
                log.debug("设置dao数据源上下文: {}", dataSourceKey);
            } catch (NoSuchMethodException e) {
                // 方法不存在，忽略
                log.debug("dao类型 {} 不支持setDataSourceKey方法", dao.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            log.warn("设置数据源上下文时发生错误", e);
        }
    }
}