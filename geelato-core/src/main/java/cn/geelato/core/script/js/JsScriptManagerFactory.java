package cn.geelato.core.script.js;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author geemeta
 */
public class JsScriptManagerFactory {
    private static Lock lock = new ReentrantLock();
    private static HashMap<String, JsScriptManager> map = new HashMap<>();

    private JsScriptManagerFactory() {
    }

    public static JsScriptManager get(String name) {
        lock.lock();
        if (!map.containsKey(name)) {
            map.put(name, new JsScriptManager());
        }
        lock.unlock();
        return map.get(name);
    }
}
