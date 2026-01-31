package cn.geelato.web.platform.event;

import java.util.Map;

public interface EsSyncService {
    void sync(String entityName, Map<String, Object> data);
}
