package cn.geelato.web.platform.resolve.draft;

import cn.geelato.web.platform.resolve.model.ResolveDraft;
import com.alibaba.fastjson2.JSON;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.J2Cache;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

@Component
public class ResolveDraftStore {
    private static final String REGION = "default";
    private static final String KEY_PREFIX = "resolve:draft:";

    private final CacheChannel cache = J2Cache.getChannel();

    public void save(ResolveDraft draft) {
        if (draft == null || Strings.isBlank(draft.getDraftId())) {
            return;
        }
        cache.set(REGION, KEY_PREFIX + draft.getDraftId(), JSON.toJSONString(draft));
    }

    public ResolveDraft get(String draftId) {
        if (Strings.isBlank(draftId)) {
            return null;
        }
        Object value = cache.get(REGION, KEY_PREFIX + draftId).getValue();
        if (value == null) {
            return null;
        }
        return JSON.parseObject(value.toString(), ResolveDraft.class);
    }
}

