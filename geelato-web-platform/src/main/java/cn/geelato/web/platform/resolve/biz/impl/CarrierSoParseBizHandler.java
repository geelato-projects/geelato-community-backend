package cn.geelato.web.platform.resolve.biz.impl;

import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoFieldKeys;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoNormalizer;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import cn.geelato.web.platform.resolve.model.MappingCandidate;
import cn.geelato.web.platform.resolve.model.ResolveDraft;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class CarrierSoParseBizHandler implements ResolveBizHandler {
    private final CarrierSoNormalizer normalizer;

    public CarrierSoParseBizHandler(CarrierSoNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public String biztag() {
        return "carrier.so.parse";
    }

    /**
     * 将 pipeline 结果归一为 SO 预览所需的标准字段集合。
     */
    @Override
    public ExtractedStructuredData extract(ResolveContext ctx, JSONObject config) {
        Object result = ctx == null ? null : ctx.getResult();
        ExtractedStructuredData extracted = normalizer.toExtracted(result);
        if (log.isDebugEnabled()) {
            log.debug("Carrier SO extracted: sourceFileName={}, fieldCount={}, resultType={}",
                    ctx == null ? null : ctx.getSourceFileName(),
                    extracted == null || extracted.getFields() == null ? 0 : extracted.getFields().size(),
                    result == null ? null : result.getClass().getSimpleName());
        }
        return extracted;
    }

    /**
     * 返回需要进入候选匹配与 AI 建议的标准字段。
     */
    @Override
    public List<String> mappingKeys(ResolveContext ctx, JSONObject config) {
        return CarrierSoFieldKeys.MAPPING_KEYS;
    }

    /**
     * 为标准字段生成预览友好的候选集；第一版以轻量枚举和原值回显为主。
     */
    @Override
    public List<MappingCandidate> queryCandidates(String fieldKey, String rawValue, ResolveContext ctx, JSONObject config) {
        List<MappingCandidate> candidates = new ArrayList<>();
        if (Strings.isBlank(fieldKey) || Strings.isBlank(rawValue)) {
            return candidates;
        }
        String upper = rawValue.trim().toUpperCase(Locale.ROOT);
        if (CarrierSoFieldKeys.CARRIER_CODE.equals(fieldKey)) {
            addCarrierCandidate(candidates, "CMA", upper, "CMA", "CMACGM");
            addCarrierCandidate(candidates, "COSCO", upper, "COSCO");
            addCarrierCandidate(candidates, "EMC", upper, "EMC", "EVERGREEN");
            addCarrierCandidate(candidates, "HMM", upper, "HMM");
            addCarrierCandidate(candidates, "MSC", upper, "MSC");
            addCarrierCandidate(candidates, "ONE", upper, "ONE");
            addCarrierCandidate(candidates, "OOCL", upper, "OOCL");
            addCarrierCandidate(candidates, "SML", upper, "SML");
            addCarrierCandidate(candidates, "WHL", upper, "WHL", "WAN HAI", "WANHAI");
            addCarrierCandidate(candidates, "ZIM", upper, "ZIM");
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        if (CarrierSoFieldKeys.PAYMENT_TERM.equals(fieldKey)) {
            addSimpleCandidate(candidates, "PREPAID", "PREPAID", upper);
            addSimpleCandidate(candidates, "COLLECT", "COLLECT", upper);
            addSimpleCandidate(candidates, "THIRD PARTY", "THIRD PARTY", upper);
            return candidates;
        }
        if (CarrierSoFieldKeys.SERVICE_TYPE.equals(fieldKey)) {
            addSimpleCandidate(candidates, "CY-CY", "CY-CY", upper);
            addSimpleCandidate(candidates, "DOOR-DOOR", "DOOR-DOOR", upper);
            addSimpleCandidate(candidates, "CY-DOOR", "CY-DOOR", upper);
            addSimpleCandidate(candidates, "DOOR-CY", "DOOR-CY", upper);
            return candidates;
        }

        MappingCandidate rawCandidate = new MappingCandidate();
        rawCandidate.setId(fieldKey + ":" + rawValue.trim());
        rawCandidate.setCode(rawValue.trim());
        rawCandidate.setName(rawValue.trim());
        candidates.add(rawCandidate);
        if (log.isDebugEnabled()) {
            log.debug("Carrier SO candidates built: fieldKey={}, rawValueLength={}, candidateCount={}",
                    fieldKey,
                    rawValue.length(),
                    candidates.size());
        }
        return candidates;
    }

    /**
     * 预览模式下不支持真实落库，避免调用方误判为已持久化。
     */
    @Override
    public Object persist(ResolveDraft confirmedDraft) {
        log.debug("Carrier SO persist blocked in preview mode: draftId={}",
                confirmedDraft == null ? null : confirmedDraft.getDraftId());
        throw new UnsupportedOperationException("carrier.so.parse preview only");
    }

    private void addCarrierCandidate(List<MappingCandidate> candidates, String code, String rawValue, String... aliases) {
        for (String alias : aliases) {
            if (rawValue.contains(alias)) {
                MappingCandidate candidate = new MappingCandidate();
                candidate.setId("carrier:" + code);
                candidate.setCode(code);
                candidate.setName(code);
                candidates.add(candidate);
                return;
            }
        }
    }

    private void addSimpleCandidate(List<MappingCandidate> candidates, String id, String name, String rawValue) {
        if (rawValue.contains(id)) {
            MappingCandidate candidate = new MappingCandidate();
            candidate.setId(id);
            candidate.setCode(id);
            candidate.setName(name);
            candidates.add(candidate);
        }
    }
}
