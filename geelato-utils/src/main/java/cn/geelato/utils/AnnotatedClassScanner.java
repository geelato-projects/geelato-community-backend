package cn.geelato.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 ASM 字节码读取的注解类扫描器，用于替代启动期 {@link ClassScanner} 的全量反射扫描。
 *
 * <p>{@link ClassScanner} 对包下<b>每个</b> {@code .class} 都执行 {@code Class.forName}
 * （加载并链接类，包含其依赖树）再用 {@code isAnnotationPresent} 判断注解，
 * 开销随 classpath 上的类数量线性增长，是启动耗时的主要来源之一。</p>
 *
 * <p>本扫描器先用 Spring 的 {@link MetadataReader}（基于 ASM 直接解析 {@code .class} 字节码，
 * 不触发 JVM 类加载）按注解<b>全名</b>过滤，<b>仅对命中注解的类</b>才执行
 * {@code ClassUtils.forName}，显著减少类加载数量与启动耗时。</p>
 *
 * <p>语义与 {@code clazz.isAnnotationPresent(annotation)} 等价：仅匹配<b>直接标注</b>在类上的
 * RUNTIME 保留注解（不解析 meta-annotation）。支持一次扫描同时匹配多个注解类型，
 * 供 GraalService/GraalVariable 等合并扫描场景使用。</p>
 *
 * @author geelato
 */
public class AnnotatedClassScanner {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatedClassScanner.class);

    /**
     * 扫描指定包（含子包）下带有任意一个给定注解的类。
     *
     * @param basePackage 基础包名（不含通配，如 {@code cn.geelato}）
     * @param annotations 需匹配的注解类型（RUNTIME 保留），至少一个；为空时返回空列表
     * @return 命中的类列表（已通过 {@code ClassUtils.forName} 加载）；扫描异常时返回空列表
     */
    @SafeVarargs
    public static List<Class<?>> scan(String basePackage, Class<? extends Annotation>... annotations) {
        if (basePackage == null || basePackage.isEmpty() || annotations == null || annotations.length == 0) {
            return Collections.emptyList();
        }
        Set<String> annotationNames = new HashSet<>();
        for (Class<? extends Annotation> annotation : annotations) {
            if (annotation != null) {
                annotationNames.add(annotation.getName());
            }
        }
        if (annotationNames.isEmpty()) {
            return Collections.emptyList();
        }

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";

        List<Class<?>> result = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String className;
                try {
                    MetadataReader reader = readerFactory.getMetadataReader(resource);
                    // 仅读字节码注解元数据，不加载类
                    Set<String> annotationTypes = reader.getAnnotationMetadata().getAnnotationTypes();
                    boolean matched = false;
                    for (String annotationType : annotationTypes) {
                        if (annotationNames.contains(annotationType)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        continue;
                    }
                    className = reader.getClassMetadata().getClassName();
                } catch (Throwable t) {
                    // 单个 .class 读取失败不应中断整体扫描
                    logger.debug("skip unreadable class resource [{}]: {}", resource, t.toString());
                    continue;
                }
                try {
                    result.add(ClassUtils.forName(className, classLoader));
                } catch (ClassNotFoundException | LinkageError e) {
                    logger.warn("无法加载命中类 [{}]: {}", className, e.toString());
                }
            }
        } catch (Exception e) {
            logger.error("扫描包 {} 出错！", basePackage, e);
        }
        return result;
    }
}
