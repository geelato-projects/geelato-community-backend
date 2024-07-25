package cn.geelato.core.biz.rules;

import cn.geelato.core.script.rule.BizMvelRuleManager;
import cn.geelato.core.script.rule.BizRuleScriptManager;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author geemeta
 */
public class BizManagerFactory {
    private static Lock lock = new ReentrantLock();
    private static HashMap<String, BizRuleScriptManager> bizRuleScriptManagerHashMap = new HashMap<>();
    private static HashMap<String, BizMvelRuleManager> bizMvelRuleManagerHashMap = new HashMap<>();

    private BizManagerFactory() {
    }

    public static BizRuleScriptManager getBizRuleScriptManager(String name) {
        lock.lock();
        if (!bizRuleScriptManagerHashMap.containsKey(name)){
            bizRuleScriptManagerHashMap.put(name, new BizRuleScriptManager());}
        lock.unlock();
        return bizRuleScriptManagerHashMap.get(name);
    }

    public static BizMvelRuleManager getBizMvelRuleManager(String name) {
        lock.lock();
        if (!bizMvelRuleManagerHashMap.containsKey(name)){
            bizMvelRuleManagerHashMap.put(name, new BizMvelRuleManager());}
        lock.unlock();
        return bizMvelRuleManagerHashMap.get(name);
    }
}
