package cn.geelato.script.boot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 整库注入声明解析：classpath 约定文件 META-INF/gl/script-kits.properties（多份跨 jar 合并，
 * 行式 别名=包名，特殊键 exclude=逗号分隔剔除清单）。
 */
final class HostKitDescriptors {

    static final String LOCATION = "META-INF/gl/script-kits.properties";
    private static final String EXCLUDE_KEY = "exclude";

    private HostKitDescriptors() {
    }

    static Map<String, String> resolveAliases() {
        return resolveAliases(loader());
    }

    static Map<String, String> resolveAliases(ClassLoader classLoader) {
        Map<String, String> merged = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (URL url : descriptorUrls(classLoader)) {
            Properties props = load(url);
            for (String alias : props.stringPropertyNames()) {
                if (EXCLUDE_KEY.equals(alias)) {
                    continue;
                }
                merge(merged, sources, alias.trim(), props.getProperty(alias).trim(), url.toString());
            }
        }
        return merged;
    }

    static List<String> resolveExcludes() {
        return resolveExcludes(loader());
    }

    static List<String> resolveExcludes(ClassLoader classLoader) {
        Set<String> merged = new LinkedHashSet<>();
        for (URL url : descriptorUrls(classLoader)) {
            String csv = load(url).getProperty(EXCLUDE_KEY);
            if (csv != null) {
                for (String item : csv.split(",")) {
                    if (!item.isBlank()) {
                        merged.add(item.trim());
                    }
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private static List<URL> descriptorUrls(ClassLoader classLoader) {
        List<URL> urls = new ArrayList<>();
        try {
            Enumeration<URL> found = classLoader.getResources(LOCATION);
            while (found.hasMoreElements()) {
                urls.add(found.nextElement());
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + LOCATION + " 失败", e);
        }
        return urls;
    }

    private static Properties load(URL url) {
        Properties props = new Properties();
        try (InputStream in = url.openStream()) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + url + " 失败", e);
        }
        return props;
    }

    private static void merge(Map<String, String> merged, Map<String, String> sources, String alias, String pkg, String source) {
        if (alias.isBlank() || pkg.isBlank()) {
            return;
        }
        String existing = merged.get(alias);
        if (existing != null && !existing.equals(pkg)) {
            throw new IllegalStateException("整库注入别名冲突: " + alias + " 已由 " + sources.get(alias) + " 映射到 " + existing
                    + "，" + source + " 又映射到 " + pkg + "；请为其中一个改用其他别名");
        }
        merged.put(alias, pkg);
        sources.put(alias, source);
    }

    private static ClassLoader loader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : HostKitDescriptors.class.getClassLoader();
    }
}
