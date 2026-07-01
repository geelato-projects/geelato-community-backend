package cn.geelato.web.platform.resolve.biz.impl;

import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ExtractedField;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import cn.geelato.web.platform.resolve.model.MappingCandidate;
import cn.geelato.web.platform.resolve.model.ResolveDraft;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractJsonResolveBizHandler implements ResolveBizHandler {
    @Override
    public ExtractedStructuredData extract(ResolveContext ctx, JSONObject config) {
        Object result = ctx == null ? null : ctx.getResult();
        List<ExtractedField> fields = new ArrayList<>();
        if (result instanceof JSONObject json) {
            for (String key : json.keySet()) {
                ExtractedField f = new ExtractedField();
                f.setKey(key);
                f.setLabel(key);
                Object v = json.get(key);
                f.setValue(v == null ? null : v.toString());
                fields.add(f);
            }
        } else if (result instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey() == null ? null : e.getKey().toString();
                if (Strings.isBlank(key)) {
                    continue;
                }
                ExtractedField f = new ExtractedField();
                f.setKey(key);
                f.setLabel(key);
                Object v = e.getValue();
                f.setValue(v == null ? null : v.toString());
                fields.add(f);
            }
        }
        ExtractedStructuredData data = new ExtractedStructuredData();
        data.setFields(fields);
        return data;
    }

    @Override
    public List<String> mappingKeys(ResolveContext ctx, JSONObject config) {
        return Collections.emptyList();
    }

    @Override
    public List<MappingCandidate> queryCandidates(String fieldKey, String rawValue, ResolveContext ctx, JSONObject config) {
        return Collections.emptyList();
    }

    @Override
    public Object persist(ResolveDraft confirmedDraft) {
        JSONObject result = new JSONObject();
        result.put("biztag", confirmedDraft == null ? null : confirmedDraft.getBiztag());
        result.put("fileId", confirmedDraft == null ? null : confirmedDraft.getFileId());
        result.put("extracted", confirmedDraft == null ? null : confirmedDraft.getExtracted());
        result.put("mapping", confirmedDraft == null ? null : confirmedDraft.getMapping());
        return result;
    }
}

