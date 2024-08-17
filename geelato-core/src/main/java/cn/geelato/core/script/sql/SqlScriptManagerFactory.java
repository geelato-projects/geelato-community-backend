package cn.geelato.core.script.sql;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author geemeta
 */
public class SqlScriptManagerFactory {
    private static final Lock lock = new ReentrantLock();
    private static final HashMap<String, SqlScriptManager> map = new HashMap<>();

    private SqlScriptManagerFactory() {
    }

    public static SqlScriptManager get(String name) {
        lock.lock();
        if (!map.containsKey(name)) {
            map.put(name, new SqlScriptManager());
        }
        lock.unlock();
        return map.get(name);
    }
}
