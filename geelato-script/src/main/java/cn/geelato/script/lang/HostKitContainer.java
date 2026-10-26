package cn.geelato.script.lang;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 整库注入容器（顶层 $kit）：成员 = 库别名 → 类简单名（首字母小写）→ 方法适配器。
 * 第三方类经 KitAdapter.ofHost 包装为 guest 原生 ProxyObject，不依赖 HostAccess 注解放行。
 */
public final class HostKitContainer implements ProxyObject {

    private final Map<String, HostLibrary> libraries = new LinkedHashMap<>();

    public void addLibrary(String alias, Map<String, KitAdapter> classes) {
        libraries.put(alias, new HostLibrary(classes));
    }

    public boolean isEmpty() {
        return libraries.isEmpty();
    }

    @Override
    public Object getMember(String key) {
        return libraries.get(key);
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromArray(libraries.keySet().toArray());
    }

    @Override
    public boolean hasMember(String key) {
        return libraries.containsKey(key);
    }

    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
        throw new UnsupportedOperationException("不支持修改 $kit");
    }

    private static final class HostLibrary implements ProxyObject {

        private final Map<String, KitAdapter> classes;

        private HostLibrary(Map<String, KitAdapter> classes) {
            this.classes = classes;
        }

        @Override
        public Object getMember(String key) {
            return classes.get(key);
        }

        @Override
        public Object getMemberKeys() {
            return ProxyArray.fromArray(classes.keySet().toArray());
        }

        @Override
        public boolean hasMember(String key) {
            return classes.containsKey(key);
        }

        @Override
        public void putMember(String key, org.graalvm.polyglot.Value value) {
            throw new UnsupportedOperationException("不支持修改 $kit");
        }
    }
}
