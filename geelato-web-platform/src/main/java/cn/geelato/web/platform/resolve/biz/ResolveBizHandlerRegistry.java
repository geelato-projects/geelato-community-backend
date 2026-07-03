package cn.geelato.web.platform.resolve.biz;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResolveBizHandlerRegistry {
    private final Map<String, ResolveBizHandler> handlers = new HashMap<>();

    public ResolveBizHandlerRegistry(List<ResolveBizHandler> handlerList) {
        if (handlerList != null) {
            for (ResolveBizHandler handler : handlerList) {
                if (handler == null || Strings.isBlank(handler.biztag())) {
                    continue;
                }
                handlers.put(handler.biztag(), handler);
            }
        }
    }

    public ResolveBizHandler get(String biztag) {
        if (Strings.isBlank(biztag)) {
            return null;
        }
        return handlers.get(biztag);
    }
}

