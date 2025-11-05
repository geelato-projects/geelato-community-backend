package cn.geelato.core.graal;

import cn.geelato.core.AbstractManager;
import cn.geelato.utils.ClassScanner;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GraalManager extends AbstractManager {
    private static GraalManager instance;

    @Getter
    private final Map<String, Object> graalServiceMap = new HashMap<>();
    @Getter
    private final Map<String, Object> graalVariableMap = new HashMap<>();
    private final Map<String, Object> globalGraalServiceMap = new HashMap<>();
    @Getter
    private final List<GraalServiceDescription> graalServiceDescriptions = new ArrayList<>();

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
        List<Class<?>> classes = ClassScanner.scan(parkeName, true, GraalService.class);
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
        List<Class<?>> classes = ClassScanner.scan(parkeName, true, GraalVariable.class);
        for (Class<?> clazz : classes) {
            try {
                initGraalVariableBean(clazz);
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

    private void initGraalVariableBean(Class<?> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        GraalVariable graalVariable = clazz.getAnnotation(GraalVariable.class);
        if (graalVariable != null) {
            String variableName = graalVariable.name();
            Object variableBean = clazz.getDeclaredConstructor().newInstance();
            graalVariableMap.put(variableName, variableBean);
        }
    }

    public Map<String, Object> getGlobalGraalVariableMap() {
        return globalGraalServiceMap;
    }
}
