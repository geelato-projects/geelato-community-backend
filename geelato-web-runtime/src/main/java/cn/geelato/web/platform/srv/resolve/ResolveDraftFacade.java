package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandlerRegistry;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoPromptProvider;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.draft.ResolveDraftStore;
import cn.geelato.web.platform.resolve.model.*;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaEnforceResult;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaEnforcer;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaRegistry;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ResolveDraftFacade {
    private final ResolveContextBuilder contextBuilder;
    private final ResolvePipelineRunner pipelineRunner;
    private final ResolveBizHandlerRegistry handlerRegistry;
    private final ResolveSchemaRegistry schemaRegistry;
    private final ResolveSchemaEnforcer schemaEnforcer;
    private final ResolveDraftStore draftStore;
    private final CarrierSoPromptProvider carrierSoPromptProvider;

    public ResolveDraftFacade(
            ResolveContextBuilder contextBuilder,
            ResolvePipelineRunner pipelineRunner,
            ResolveBizHandlerRegistry handlerRegistry,
            ResolveSchemaRegistry schemaRegistry,
            ResolveSchemaEnforcer schemaEnforcer,
            ResolveDraftStore draftStore,
            CarrierSoPromptProvider carrierSoPromptProvider
    ) {
        this.contextBuilder = contextBuilder;
        this.pipelineRunner = pipelineRunner;
        this.handlerRegistry = handlerRegistry;
        this.schemaRegistry = schemaRegistry;
        this.schemaEnforcer = schemaEnforcer;
        this.draftStore = draftStore;
        this.carrierSoPromptProvider = carrierSoPromptProvider;
    }

    /**
     * 生成解析草稿，返回标准字段、匹配建议与步骤结果，供前端预览和修正。
     */
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
            if (log.isDebugEnabled()) {
                log.debug("Create resolve draft received: biztag={}, appId={}, tenantCode={}, hasFileId={}, hasUploadFile={}, configLength={}",
                        biztag,
                        appId,
                        tenantCode,
                        Strings.isNotBlank(fileId),
                        file != null && !file.isEmpty(),
                        config == null ? 0 : config.length());
            }
            ctx = contextBuilder.build(fileId, file, biztag, config, appId, tenantCode);
            ensureDefaultPrompt(ctx);
            ResolveContext resultCtx = pipelineRunner.run(ctx);
            JSONObject configObj = resultCtx.getPayload();

            ExtractedStructuredData extracted = handler.extract(resultCtx, configObj);
            if (log.isDebugEnabled()) {
                log.debug("Draft extract finished: biztag={}, fieldCount={}",
                        biztag,
                        extracted == null || extracted.getFields() == null ? 0 : extracted.getFields().size());
            }
            if (Strings.isNotBlank(biztag) && schemaRegistry.exists(biztag)) {
                long schemaStart = System.currentTimeMillis();
                ResolveSchemaEnforceResult enforceResult = schemaEnforcer.enforce(biztag, extracted);
                if (log.isDebugEnabled()) {
                    log.debug("Draft schema enforced: biztag={}, success={}, fixedCount={}, errorCount={}",
                            biztag,
                            enforceResult.isSuccess(),
                            enforceResult.getFixedCount(),
                            enforceResult.getErrors() == null ? 0 : enforceResult.getErrors().size());
                }
                ResolveStepResult schemaStep = new ResolveStepResult();
                schemaStep.setArtifactId("schema.enforce");
                schemaStep.setSuccess(enforceResult.isSuccess());
                schemaStep.setCostMs(System.currentTimeMillis() - schemaStart);
                schemaStep.setOutput(enforceResult.getErrors() == null ? 0 : enforceResult.getErrors().size());
                if (!enforceResult.isSuccess() && enforceResult.getErrors() != null && !enforceResult.getErrors().isEmpty()) {
                    schemaStep.setErrorMsg(enforceResult.getErrors().get(0));
                }
                resultCtx.getSteps().add(schemaStep);
                if (!enforceResult.isSuccess()) {
                    ResolveDraft failed = new ResolveDraft();
                    failed.setStatus(ResolveDraftStatusEnum.FAILED);
                    failed.setBiztag(biztag);
                    failed.setFileId(resultCtx.getFileId());
                    failed.setFileName(resultCtx.getSourceFileName());
                    failed.setConfig(configObj);
                    failed.setExtracted(extracted);
                    failed.setSteps(resultCtx.getSteps());
                    failed.setErrorMsg(schemaStep.getErrorMsg());
                    return ApiResult.fail(failed, schemaStep.getErrorMsg() == null ? "schema validation failed" : schemaStep.getErrorMsg());
                }
                extracted = enforceResult.getNormalizedExtracted();
            }
            long mapStart = System.currentTimeMillis();
            List<MappingSuggestion> mapping = buildMapping(handler, extracted, resultCtx, configObj);
            if (log.isDebugEnabled()) {
                log.debug("Draft mapping finished: biztag={}, mappingCount={}",
                        biztag,
                        mapping == null ? 0 : mapping.size());
            }
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
            draft.setSteps(resultCtx.getSteps());
            long saveStart = System.currentTimeMillis();
            draftStore.save(draft);
            ResolveStepResult saveStep = new ResolveStepResult();
            saveStep.setArtifactId("draft.save");
            saveStep.setSuccess(true);
            saveStep.setCostMs(System.currentTimeMillis() - saveStart);
            saveStep.setOutput(draft.getDraftId());
            resultCtx.getSteps().add(saveStep);
            draft.setSteps(resultCtx.getSteps());
            draftStore.save(draft);

            if (log.isDebugEnabled()) {
                log.debug("Resolve draft created: draftId={}, biztag={}, steps={}",
                        draft.getDraftId(),
                        draft.getBiztag(),
                        draft.getSteps() == null ? 0 : draft.getSteps().size());
            }
            return ApiResult.success(draft);
        } catch (Exception e) {
            log.debug("Create resolve draft failed: biztag={}, error={}", biztag, e.getMessage());
            ResolveDraft failed = new ResolveDraft();
            failed.setStatus(ResolveDraftStatusEnum.FAILED);
            failed.setBiztag(biztag);
            if (ctx != null) {
                failed.setFileId(ctx.getFileId());
                failed.setFileName(ctx.getSourceFileName());
                failed.setSteps(ctx.getSteps());
            }
            failed.setErrorMsg(e.getMessage());
            return ApiResult.fail(failed, e.getMessage());
        } finally {
            contextBuilder.cleanup(ctx);
        }
    }

    /**
     * 查询单个草稿详情。
     */
    public ApiResult<?> getDraft(String draftId) {
        ResolveDraft draft = draftStore.get(draftId);
        if (draft == null) {
            log.debug("Resolve draft query missed: draftId={}", draftId);
            return ApiResult.fail("draft not found");
        }
        if (log.isDebugEnabled()) {
            log.debug("Resolve draft query hit: draftId={}, status={}", draftId, draft.getStatus());
        }
        return ApiResult.success(draft);
    }

    /**
     * 更新草稿中允许被人工修正的 extracted 与 mapping 数据。
     */
    public ApiResult<?> updateDraft(String draftId, ResolveDraftUpdateRequest req) {
        ResolveDraft draft = draftStore.get(draftId);
        if (draft == null) {
            log.debug("Resolve draft update missed: draftId={}", draftId);
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
        if (log.isDebugEnabled()) {
            log.debug("Resolve draft updated: draftId={}, hasExtractedUpdate={}, hasMappingUpdate={}",
                    draftId,
                    req != null && req.getExtracted() != null,
                    req != null && req.getMapping() != null);
        }
        return ApiResult.success(draft);
    }

    /**
     * 确认草稿并调用对应 biz handler 的持久化逻辑。
     */
    public ApiResult<?> confirm(String draftId) {
        ResolveDraft draft = draftStore.get(draftId);
        if (draft == null) {
            log.debug("Resolve draft confirm missed: draftId={}", draftId);
            return ApiResult.fail("draft not found");
        }
        if (draft.getStatus() == ResolveDraftStatusEnum.PERSISTED) {
            log.debug("Resolve draft already persisted: draftId={}", draftId);
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
            if (log.isDebugEnabled()) {
                log.debug("Resolve draft confirmed: draftId={}, biztag={}, persistResultType={}",
                        draftId,
                        draft.getBiztag(),
                        persistResult == null ? null : persistResult.getClass().getSimpleName());
            }
            return ApiResult.success(draft);
        } catch (Exception e) {
            draft.setStatus(ResolveDraftStatusEnum.FAILED);
            draft.setErrorMsg(e.getMessage());
            draft.setUpdatedAt(System.currentTimeMillis());
            draftStore.save(draft);
            log.debug("Resolve draft confirm failed: draftId={}, biztag={}, error={}",
                    draftId,
                    draft.getBiztag(),
                    e.getMessage());
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
            MappingSuggestion ms = new MappingSuggestion();
            ms.setKey(key);
            ms.setRawValue(rawValue);
            ms.setCandidates(candidates);
            result.add(ms);
            if (log.isDebugEnabled()) {
                log.debug("Resolve draft mapping suggestion built: biztag={}, key={}, candidateCount={}, hasSuggested={}",
                        ctx == null ? null : ctx.getBiztag(),
                        key,
                        candidates == null ? 0 : candidates.size(),
                        false);
            }
        }
        return result;
    }

    private void ensureDefaultPrompt(ResolveContext ctx) {
        if (ctx == null || !"carrier.so.parse".equals(ctx.getBiztag())) {
            return;
        }
        JSONObject params = ctx.getParams();
        if (params == null) {
            params = new JSONObject();
            ctx.setParams(params);
        }
        if (Strings.isNotBlank(params.getString("prompt"))) {
            return;
        }
        params.put("prompt", carrierSoPromptProvider.defaultPrompt());
        if (ctx.getPayload() != null) {
            ctx.getPayload().put("params", params);
        }
        log.debug("Default carrier.so prompt injected for draft: biztag={}, sourceFileName={}",
                ctx.getBiztag(),
                ctx.getSourceFileName());
    }
}
