package cn.geelato.core.script.db;

import cn.geelato.core.script.sql.SqlScriptManager;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DbScriptManagerFactory {
    private static final Lock lock = new ReentrantLock();
    private static final HashMap<String, DbScriptManager> map = new HashMap<>();

    private DbScriptManagerFactory() {
    }

    public static DbScriptManager get(String name) {
        lock.lock();
        if (!map.containsKey(name)) {
            map.put(name, new DbScriptManager());
        }
        lock.unlock();
        return map.get(name);
    }
}
