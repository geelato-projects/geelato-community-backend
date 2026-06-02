package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.resolve.ai.AiMatchClient;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandlerRegistry;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.draft.ResolveDraftStore;
import cn.geelato.web.platform.resolve.model.*;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ResolveDraftFacade {
    private final ResolveContextBuilder contextBuilder;
    private final ResolvePipelineRunner pipelineRunner;
    private final ResolveBizHandlerRegistry handlerRegistry;
    private final ResolveDraftStore draftStore;
    private final AiMatchClient aiMatchClient;

    public ResolveDraftFacade(
            ResolveContextBuilder contextBuilder,
            ResolvePipelineRunner pipelineRunner,
            ResolveBizHandlerRegistry handlerRegistry,
            ResolveDraftStore draftStore,
            AiMatchClient aiMatchClient
    ) {
        this.contextBuilder = contextBuilder;
        this.pipelineRunner = pipelineRunner;
        this.handlerRegistry = handlerRegistry;
        this.draftStore = draftStore;
        this.aiMatchClient = aiMatchClient;
    }

    public ApiResult<?> createDraft(String fileId, MultipartFile file, String biztag, String config, String appId, String tenantCode) {
        if (Strings.isBlank(biztag)) {
            return ApiResult.fail("biztag is required");
        }
        ResolveBizHandler handler = handlerRegistry.get(biztag);
        if (handler == null) {
            return ApiResult.fail("biztag not supported: " + biztag);
        }

        ResolveContext ctx = null;
        try {
            ctx = contextBuilder.build(fileId, file, biztag, config, appId, tenantCode);
            ResolveContext resultCtx = pipelineRunner.run(ctx);
            JSONObject configObj = resultCtx.getPayload();

            ExtractedStructuredData extracted = handler.extract(resultCtx, configObj);
            long mapStart = System.currentTimeMillis();
            List<MappingSuggestion> mapping = buildMapping(handler, extracted, resultCtx, configObj);
            ResolveStepResult mappingStep = new ResolveStepResult();
            mappingStep.setArtifactId("mapping.suggest");
            mappingStep.setSuccess(true);
            mappingStep.setCostMs(System.currentTimeMillis() - mapStart);
            mappingStep.setOutput(mapping == null ? 0 : mapping.size());
            resultCtx.getSteps().add(mappingStep);

            ResolveDraft draft = new ResolveDraft();
            draft.setDraftId(UUID.randomUUID().toString().replace("-", ""));
            draft.setStatus(ResolveDraftStatusEnum.DRAFT);
            draft.setBiztag(biztag);
            draft.setFileId(resultCtx.getFileId());
            draft.setFileName(resultCtx.getSourceFileName());
            draft.setConfig(configObj);
            draft.setExtracted(extracted);
            draft.setMapping(mapping);
            draft.setCreatedAt(System.currentTimeMillis());
            draft.setUpdatedAt(System.currentTimeMillis());
            long saveStart = System.currentTimeMillis();
            draftStore.save(draft);
            ResolveStepResult saveStep = new ResolveStepResult();
            saveStep.setArtifactId("draft.save");
            saveStep.setSuccess(true);
            saveStep.setCostMs(System.currentTimeMillis() - saveStart);
            saveStep.setOutput(draft.getDraftId());
            resultCtx.getSteps().add(saveStep);
            draft.setSteps(resultCtx.getSteps());

            return ApiResult.success(draft);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        } finally {
            contextBuilder.cleanup(ctx);
        }
    }

    public ApiResult<?> getDraft(String draftId) {
        ResolveDraft draft = draftStore.get(draftId);
        if (draft == null) {
            return ApiResult.fail("draft not found");
        }
        return ApiResult.success(draft);
    }

    public ApiResult<?> updateDraft(String draftId, ResolveDraftUpdateRequest req) {
        ResolveDraft draft = draftStore.get(draftId);
        if (draft == null) {
            return ApiResult.fail("draft not found");
        }
        if (req != null) {
            if (req.getExtracted() != null) {
                draft.setExtracted(req.getExtracted());
            }
            if (req.getMapping() != null) {
                draft.setMapping(req.getMapping());
            }
        }
        draft.setUpdatedAt(System.currentTimeMillis());
        draftStore.save(draft);
        return ApiResult.success(draft);
    }

    public ApiResult<?> confirm(String draftId) {
        ResolveDraft draft = draftStore.get(draftId);
        if (draft == null) {
            return ApiResult.fail("draft not found");
        }
        if (draft.getStatus() == ResolveDraftStatusEnum.PERSISTED) {
            return ApiResult.success(draft);
        }
        ResolveBizHandler handler = handlerRegistry.get(draft.getBiztag());
        if (handler == null) {
            return ApiResult.fail("biztag not supported: " + draft.getBiztag());
        }
        try {
            draft.setStatus(ResolveDraftStatusEnum.CONFIRMED);
            Object persistResult = handler.persist(draft);
            draft.setPersistResult(persistResult);
            draft.setStatus(ResolveDraftStatusEnum.PERSISTED);
            draft.setUpdatedAt(System.currentTimeMillis());
            draftStore.save(draft);
            return ApiResult.success(draft);
        } catch (Exception e) {
            draft.setStatus(ResolveDraftStatusEnum.FAILED);
            draft.setErrorMsg(e.getMessage());
            draft.setUpdatedAt(System.currentTimeMillis());
            draftStore.save(draft);
            return ApiResult.fail(e.getMessage());
        }
    }

    private List<MappingSuggestion> buildMapping(ResolveBizHandler handler, ExtractedStructuredData extracted, ResolveContext ctx, JSONObject config) {
        List<MappingSuggestion> result = new ArrayList<>();
        if (extracted == null || extracted.getFields() == null || extracted.getFields().isEmpty()) {
            return result;
        }

        List<String> keys = handler.mappingKeys(ctx, config);
        if (keys == null || keys.isEmpty()) {
            keys = new ArrayList<>();
            for (ExtractedField f : extracted.getFields()) {
                if (f != null && Strings.isNotBlank(f.getKey())) {
                    keys.add(f.getKey());
                }
            }
        }

        for (String key : keys) {
            String rawValue = null;
            for (ExtractedField f : extracted.getFields()) {
                if (f != null && key.equals(f.getKey())) {
                    rawValue = f.getValue();
                    break;
                }
            }
            if (Strings.isBlank(rawValue)) {
                continue;
            }
            List<MappingCandidate> candidates = handler.queryCandidates(key, rawValue, ctx, config);
            AiSuggestion suggested = aiMatchClient.match(ctx.getBiztag(), key, rawValue, candidates);
            MappingSuggestion ms = new MappingSuggestion();
            ms.setKey(key);
            ms.setRawValue(rawValue);
            ms.setCandidates(candidates);
            ms.setSuggested(suggested);
            result.add(ms);
        }
        return result;
    }
}
