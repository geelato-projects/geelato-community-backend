package cn.geelato.core.script;

import cn.geelato.core.script.sql.SqlScriptManager;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManagerFactory<T> {

    private static final Lock lock = new ReentrantLock();
    private static final HashMap<String, AbstractScriptManager> map = new HashMap<>();

    public static  <T extends AbstractScriptManager> T get(String name) {
        lock.lock();
        if (!map.containsKey(name)) {
//            map.put(name,clazz.newInstance());
        }
        lock.unlock();
        return (T) map.get(name);
    }
}
