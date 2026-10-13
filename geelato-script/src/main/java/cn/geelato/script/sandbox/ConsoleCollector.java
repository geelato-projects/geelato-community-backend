package cn.geelato.script.sandbox;

import lombok.Getter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * console 输出收集（guest 侧 ProxyObject，绕开 HostAccess）：单条截断 2000 字符、总量 100 条。
 */
@Getter
public class ConsoleCollector {

    private static final int MAX_ENTRIES = 100;
    private static final int MAX_LENGTH = 2000;

    private final List<String> logs = new ArrayList<>();

    public void inject(Context context) {
        Value bindings = context.getBindings("js");
        Map<String, Object> methods = new java.util.LinkedHashMap<>();
        methods.put("log", (ProxyExecutable) args -> append("log", args));
        methods.put("info", (ProxyExecutable) args -> append("info", args));
        methods.put("warn", (ProxyExecutable) args -> append("warn", args));
        methods.put("error", (ProxyExecutable) args -> append("error", args));
        methods.put("debug", (ProxyExecutable) args -> append("log", args));
        bindings.putMember("console", ProxyObject.fromMap(methods));
    }

    private Object append(String level, Value... args) {
        if (logs.size() >= MAX_ENTRIES) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[").append(level).append("] ");
        for (int i = 0; args != null && i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(stringify(args[i]));
        }
        String line = sb.length() > MAX_LENGTH ? sb.substring(0, MAX_LENGTH) + "…(截断)" : sb.toString();
        logs.add(line);
        return null;
    }

    private static String stringify(Value v) {
        if (v == null || v.isNull()) {
            return "null";
        }
        try {
            if (v.isString()) {
                return v.asString();
            }
            if (v.isNumber() || v.isBoolean()) {
                return v.toString();
            }
            if (v.hasArrayElements()) {
                StringBuilder sb = new StringBuilder("[");
                long n = Math.min(v.getArraySize(), 50);
                for (long i = 0; i < n; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(stringify(v.getArrayElement(i)));
                }
                return sb.append(']').toString();
            }
            if (v.hasMembers()) {
                StringBuilder sb = new StringBuilder("{");
                int count = 0;
                for (String key : v.getMemberKeys()) {
                    if (count++ >= 30) {
                        sb.append("…");
                        break;
                    }
                    if (count > 1) {
                        sb.append(',');
                    }
                    sb.append(key).append(':').append(stringify(v.getMember(key)));
                }
                return sb.append('}').toString();
            }
            return v.toString();
        } catch (Exception e) {
            return "<unprintable>";
        }
    }
}
