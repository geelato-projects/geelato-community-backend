package cn.geelato.web.platform.resolve.biz;

import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import cn.geelato.web.platform.resolve.model.MappingCandidate;
import cn.geelato.web.platform.resolve.model.ResolveDraft;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

public interface ResolveBizHandler {
    String biztag();

    ExtractedStructuredData extract(ResolveContext ctx, JSONObject config);

    List<String> mappingKeys(ResolveContext ctx, JSONObject config);

    List<MappingCandidate> queryCandidates(String fieldKey, String rawValue, ResolveContext ctx, JSONObject config);

    Object persist(ResolveDraft confirmedDraft);
}

