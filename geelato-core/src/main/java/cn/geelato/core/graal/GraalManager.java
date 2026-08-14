package cn.geelato.core.graal;

import cn.geelato.core.AbstractManager;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.utils.AnnotatedClassScanner;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
public class GraalManager extends AbstractManager {
    private static GraalManager instance;

    private final Map<String, Object> graalServiceMap = new HashMap<>();
    private final Map<String, Object> graalVariableMap = new HashMap<>();
    private final Map<String, Object> globalGraalServiceMap = new HashMap<>();
    private final List<GraalServiceDescription> graalServiceDescriptions = new ArrayList<>();

    /**
     * Graal 上下文懒初始化支持：允许启动期把扫描预热放到后台线程，使应用更早对外提供服务；
     * 任何运行期访问（getGraalServiceMap 等）通过 {@link #ensureInitialized()} 兜底等待完成，
     * 绝不会读到半初始化数据。最坏情况下首访问者承担等同旧版的初始化耗时，仅时机转移。
     */
    private volatile boolean initialized = false;
    private volatile Throwable initError = null;
    private String[] pendingPackages = null;
    private CompletableFuture<Void> initFuture = null;
    private final Object initLock = new Object();
    private final Executor graalWarmerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "graal-context-warmer");
        t.setDaemon(true);
        return t;
    });

    private GraalManager() {
        log.info("GraalManager Instancing...");
    }

    public static GraalManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new GraalManager();
        }
        lock.unlock();
        return instance;
    }

    public void initGraalService(String parkeName) {
        log.info("开始从包{}中扫描到包含注解{}的服务类......", parkeName, GraalService.class);
        List<Class<?>> classes = AnnotatedClassScanner.scan(parkeName, GraalService.class);
        for (Class<?> clazz : classes) {
            try {
                initGraalServiceBean(clazz);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void initGraalVariable(String parkeName) {
        log.info("开始从包{}中扫描到包含注解{}的参数类......", parkeName, GraalVariable.class);
        List<Class<?>> classes = AnnotatedClassScanner.scan(parkeName, GraalVariable.class);
        for (Class<?> clazz : classes) {
            try {
                initGraalVariableBean(clazz);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 后台异步初始化 Graal 上下文（合并 @GraalService 与 @GraalVariable 单趟扫描）。
     * 不阻塞调用线程，通常由启动引导在 ApplicationReady 之前调用一次。
     * 完成后访问点立即可用；未完成时访问点会等待（见 {@link #ensureInitialized()}）。
     */
    public void initGraalContextAsync(String... packageNames) {
        synchronized (initLock) {
            if (initFuture != null || initialized) {
                return;
            }
            pendingPackages = packageNames;
            initFuture = CompletableFuture.runAsync(this::doInitGraalContext, graalWarmerExecutor);
        }
    }

    /**
     * 同步初始化 Graal 上下文（当前线程内完成扫描），与历史启动行为一致。
     */
    public void initGraalContextSync(String... packageNames) {
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            if (initFuture == null) {
                pendingPackages = packageNames;
            }
        }
        ensureInitialized();
    }

    /**
     * 访问点兜底：确保 Graal 上下文初始化完成。已启动则等待其完成；未启动则当场同步启动。
     */
    public void ensureInitialized() {
        if (initialized) {
            return;
        }
        CompletableFuture<Void> future;
        synchronized (initLock) {
            if (initFuture == null) {
                initFuture = CompletableFuture.runAsync(this::doInitGraalContext, graalWarmerExecutor);
            }
            future = initFuture;
        }
        future.join();
        if (initError != null) {
            throw new RuntimeException("Graal 上下文初始化失败", initError);
        }
    }

    private void doInitGraalContext() {
        try {
            String[] packages = pendingPackages;
            if (packages != null) {
                for (String packageName : packages) {
                    initGraalContextScan(packageName);
                }
            }
            initialized = true;
        } catch (Throwable t) {
            initError = t;
            throw t;
        }
    }

    /**
     * 对单个包合并扫描 @GraalService 与 @GraalVariable（单趟 classpath 遍历）。
     */
    private void initGraalContextScan(String packageName) {
        log.info("开始从包{}中扫描 GraalService/GraalVariable...", packageName);
        List<Class<?>> classes = AnnotatedClassScanner.scan(packageName, GraalService.class, GraalVariable.class);
        for (Class<?> clazz : classes) {
            boolean isService = clazz.isAnnotationPresent(GraalService.class);
            boolean isVariable = clazz.isAnnotationPresent(GraalVariable.class);
            try {
                if (isService) {
                    initGraalServiceBean(clazz);
                }
                if (isVariable) {
                    initGraalVariableBean(clazz);
                }
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void initGraalServiceBean(Class<?> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        GraalService graalService = clazz.getAnnotation(GraalService.class);
        if (graalService != null) {
            String serviceName = graalService.name();
            String built = graalService.built();
            Object serviceBean = clazz.getDeclaredConstructor().newInstance();
            injectDynamicDataSource(serviceBean);
            if ("true".equals(built)) {
                globalGraalServiceMap.put(serviceName, serviceBean);
            } else {
                graalServiceMap.put(serviceName, serviceBean);
            }

            GraalServiceDescription serviceDescription = new GraalServiceDescription();
            serviceDescription.setServiceName(serviceName);
            // 注意：注解字段为 descrption（原始拼写）
            serviceDescription.setDescription(graalService.descrption());

            List<GraalFunctionDescription> functionDescriptions = new ArrayList<>();
            for (Method method : clazz.getDeclaredMethods()) {
                GraalFunction graalFunction = method.getAnnotation(GraalFunction.class);
                if (graalFunction != null) {
                    GraalFunctionDescription fdesc = new GraalFunctionDescription();
                    // 方法名作为函数名称
                    fdesc.setName(method.getName());
                    fdesc.setExample(graalFunction.example());
                    fdesc.setDescription(graalFunction.description());
                    functionDescriptions.add(fdesc);
                }
            }
            serviceDescription.setFunctions(functionDescriptions);
            graalServiceDescriptions.add(serviceDescription);
        }
    }

    private void injectDynamicDataSource(Object serviceBean) {
        Class<?> current = serviceBean.getClass();
        while (current != null) {
            for (java.lang.reflect.Field f : current.getDeclaredFields()) {
                if (!"cn.geelato.core.orm.Dao".equals(f.getType().getName())) {
                    continue;
                }
//                if (!hasDynamicDsAnnotation(f)) {
//                    continue;
//                }
                cn.geelato.core.orm.Dao daoBean = resolveSpringDaoBean();
                if (daoBean != null) {
                    try {
                        f.setAccessible(true);
                        f.set(serviceBean, daoBean);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    private boolean hasDynamicDsAnnotation(java.lang.reflect.Field f) {
        for (java.lang.annotation.Annotation a : f.getDeclaredAnnotations()) {
            String n = a.annotationType().getName();
            if ("cn.geelato.datasource.annotation.UseDynamicDataSource".equals(n)) {
                return true;
            }
        }
        return false;
    }

    private cn.geelato.core.orm.Dao resolveSpringDaoBean() {
        try {
            return BeansUtils.getBean("dynamicDao", cn.geelato.core.orm.Dao.class);
        } catch (Throwable ignored) {
        }
        try {
            return BeansUtils.getBean(cn.geelato.core.orm.Dao.class);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void initGraalVariableBean(Class<?> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        GraalVariable graalVariable = clazz.getAnnotation(GraalVariable.class);
        if (graalVariable != null) {
            String variableName = graalVariable.name();
            Object variableBean = clazz.getDeclaredConstructor().newInstance();
            graalVariableMap.put(variableName, variableBean);
        }
    }

    public Map<String, Object> getGraalServiceMap() {
        ensureInitialized();
        return graalServiceMap;
    }

    public Map<String, Object> getGraalVariableMap() {
        ensureInitialized();
        return graalVariableMap;
    }

    public List<GraalServiceDescription> getGraalServiceDescriptions() {
        ensureInitialized();
        return graalServiceDescriptions;
    }

    public Map<String, Object> getGlobalGraalVariableMap() {
        ensureInitialized();
        return globalGraalServiceMap;
    }
}
