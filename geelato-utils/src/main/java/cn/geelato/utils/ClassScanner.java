package cn.geelato.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * * 用于获取指定包名下的所有类名.
 * 并可设置是否遍历该包名下的子包的类名.
 * 并可通过Annotation(内注)来过滤，避免一些内部类的干扰.
 */
public class ClassScanner {

    private static final Logger logger = LoggerFactory.getLogger(ClassScanner.class);

    public static List<Class<?>> scan(String pkgName, boolean isRecursive, Class<? extends Annotation> annotation) {
        List<Class<?>> classList = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            // 按文件的形式去查找
            String strFile = pkgName.replaceAll("\\.", "/");
            Enumeration<URL> urls = loader.getResources(strFile);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (url != null) {
                    String protocol = url.getProtocol();
                    String pkgPath = url.getPath();

                    System.out.println("protocol:" + protocol + " pkgPath:" + pkgPath);

                    if ("file".equals(protocol)) {
                        // 本地自己可见的代码
                        findClassName(classList, pkgName, pkgPath, isRecursive, annotation);
                    } else if ("jar".equals(protocol)) {
                        // 引用第三方jar的代码
                        findClassName(classList, pkgName, url, isRecursive, annotation);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("扫描包" + pkgName + "出错！", e);
        }
        return classList;
    }

    public static void findClassName(List<Class<?>> clazzList, String pkgName, String pkgPath, boolean isRecursive, Class<? extends Annotation> annotation) {
        if (clazzList == null) {
            return;
        }
        File[] files = filterClassFiles(pkgPath);// 过滤出.class文件及文件夹
//        System.out.println("files:" + ((files == null) ? "null" : "length=" + files.length));
        if (files != null) {
            for (File f : files) {
                String fileName = f.getName();
                if (f.isFile()) {
                    // .class 文件的情况
                    String clazzName = getClassName(pkgName, fileName);
                    addClassName(clazzList, clazzName, annotation);
                } else {
                    // 文件夹的情况
                    if (isRecursive) {
                        // 需要继续查找该文件夹/包名下的类
                        String subPkgName = pkgName + "." + fileName;
                        String subPkgPath = pkgPath + "/" + fileName;
                        findClassName(clazzList, subPkgName, subPkgPath, true, annotation);
                    }
                }
            }
        }
    }

    /**
     * 查找指定包名下的类名，并将其添加到给定的类列表中。
     * 该方法通过遍历指定的JAR文件，查找所有符合条件的类名，并将其添加到传入的类列表中。
     * 如果设置了递归选项，则还会查找子包中的类。
     *
     * @param clazzList   类列表，用于存储找到的类名
     * @param pkgName     包名，用于筛选符合条件的类
     * @param url         JAR文件的URL
     * @param isRecursive 是否递归查找子包中的类
     * @param annotation  注解类型，用于筛选具有指定注解的类（可选）
     * @throws IOException 如果在读取JAR文件时发生I/O错误，则抛出此异常
     */
    public static void findClassName(List<Class<?>> clazzList, String pkgName, URL url, boolean isRecursive, Class<? extends Annotation> annotation) throws IOException {
        JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
        JarFile jarFile = jarURLConnection.getJarFile();
        System.out.println("jarFile:" + jarFile.getName());
        Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = jarEntries.nextElement();
            String jarEntryName = jarEntry.getName(); // 类似：sun/security/internal/interfaces/TlsMasterSecret.class
            String clazzName = jarEntryName.replace("/", ".");
            int endIndex = clazzName.lastIndexOf(".");
            String prefix = null;
            if (endIndex > 0) {
                String prefix_name = clazzName.substring(0, endIndex);
                endIndex = prefix_name.lastIndexOf(".");
                if (endIndex > 0) {
                    prefix = prefix_name.substring(0, endIndex);
                }
            }
            if (prefix != null && jarEntryName.endsWith(".class")) {
//              System.out.println("prefix:" + prefix +" pkgName:" + pkgName);
                if (prefix.equals(pkgName)) {
//                    System.out.println("jar entryName:" + jarEntryName);
                    addClassName(clazzList, clazzName, annotation);
                } else if (isRecursive && prefix.startsWith(pkgName)) {
                    // 遍历子包名：子类
//                    System.out.println("jar entryName:" + jarEntryName + " isRecursive:" + isRecursive);
                    addClassName(clazzList, clazzName, annotation);
                }
            }
        }
    }

    private static File[] filterClassFiles(String pkgPath) {
        if (pkgPath == null) {
            return null;
        }
        // 接收 .class 文件 或 类文件夹
        return new File(pkgPath).listFiles(file -> (file.isFile() && file.getName().endsWith(".class")) || file.isDirectory());
    }

    private static String getClassName(String pkgName, String fileName) {
        int endIndex = fileName.lastIndexOf(".");
        String clazz = null;
        if (endIndex >= 0) {
            clazz = fileName.substring(0, endIndex);
        }
        String clazzName = null;
        if (clazz != null) {
            clazzName = pkgName + "." + clazz;
        }
        return clazzName;
    }

    private static void addClassName(List<Class<?>> clazzList, String clazzName, Class<? extends Annotation> annotation) {
        if (clazzList != null && clazzName != null) {
            Class<?> clazz = null;
            try {
                int index = clazzName.lastIndexOf(".class");
                String name = clazzName;
                if (index > 0) {
                    name = clazzName.substring(0, index);
                }
                clazz = Class.forName(name,false,Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                logger.error("", e);
            }
            if (clazz != null) {
                if (annotation == null) {
                    clazzList.add(clazz);
                } else if (clazz.isAnnotationPresent(annotation)) {
                    clazzList.add(clazz);
                }
            }
        }
    }
}
