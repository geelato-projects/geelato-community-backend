package cn.geelato.web.platform.resolve.task;

import cn.geelato.web.platform.resolve.model.ResolveTask;
import com.alibaba.fastjson2.JSON;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.J2Cache;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
public class ResolveTaskStore {
    private static final String REGION = "default";
    private static final String KEY_PREFIX = "resolve:task:";

    private final CacheChannel cache = J2Cache.getChannel();

    public void save(ResolveTask task) {
        if (task == null || Strings.isBlank(task.getTaskId())) {
            return;
        }
        cache.set(REGION, KEY_PREFIX + task.getTaskId(), JSON.toJSONString(task));
    }

    public ResolveTask get(String taskId) {
        if (Strings.isBlank(taskId)) {
            return null;
        }
        Object value = cache.get(REGION, KEY_PREFIX + taskId).getValue();
        if (value == null) {
            return null;
        }
        return JSON.parseObject(value.toString(), ResolveTask.class);
    }
}

