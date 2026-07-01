package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandler;
import cn.geelato.web.platform.resolve.biz.ResolveBizHandlerRegistry;
import cn.geelato.web.platform.resolve.biz.carrierso.CarrierSoPromptProvider;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ExtractedStructuredData;
import cn.geelato.web.platform.resolve.model.ResolveResponse;
import cn.geelato.web.platform.resolve.model.ResolveStatusEnum;
import cn.geelato.web.platform.resolve.model.ResolveStandardResult;
import cn.geelato.web.platform.resolve.model.ResolveSubmitResponse;
import cn.geelato.web.platform.resolve.model.ResolveTask;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaEnforceResult;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaEnforcer;
import cn.geelato.web.platform.resolve.schema.ResolveSchemaRegistry;
import cn.geelato.web.platform.resolve.model.ResolveStepResult;
import cn.geelato.web.platform.resolve.task.ResolveTaskStore;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class ResolveFacade {
    private final ResolveContextBuilder contextBuilder;
    private final ResolvePipelineRunner pipelineRunner;
    private final ResolveBizHandlerRegistry handlerRegistry;
    private final ResolveSchemaRegistry schemaRegistry;
    private final ResolveSchemaEnforcer schemaEnforcer;
    private final ResolveTaskStore taskStore;
    private final Executor resolveExecutor;
    private final CarrierSoPromptProvider carrierSoPromptProvider;

    public ResolveFacade(
            ResolveContextBuilder contextBuilder,
            ResolvePipelineRunner pipelineRunner,
            ResolveBizHandlerRegistry handlerRegistry,
            ResolveSchemaRegistry schemaRegistry,
            ResolveSchemaEnforcer schemaEnforcer,
            ResolveTaskStore taskStore,
            @Qualifier("resolveExecutor") Executor resolveExecutor,
            CarrierSoPromptProvider carrierSoPromptProvider
    ) {
        this.contextBuilder = contextBuilder;
        this.pipelineRunner = pipelineRunner;
        this.handlerRegistry = handlerRegistry;
        this.schemaRegistry = schemaRegistry;
        this.schemaEnforcer = schemaEnforcer;
        this.taskStore = taskStore;
        this.resolveExecutor = resolveExecutor;
        this.carrierSoPromptProvider = carrierSoPromptProvider;
    }

    /**
     * 同步执行解析，并在有 schema 的场景下返回标准化结果。
     */
    public ApiResult<?> resolve(String fileId, MultipartFile file, String biztag, String config, String appId, String tenantCode) {
        ResolveContext ctx = null;
        try {
            if (log.isDebugEnabled()) {
                log.debug("Resolve request received: biztag={}, appId={}, tenantCode={}, hasFileId={}, hasUploadFile={}, configLength={}",
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
            ResolveResponse response = new ResolveResponse();
            Object result = buildSchemaAwareResult(resultCtx);
            response.setResult(result);
            response.setSteps(resultCtx.getSteps());
            if (log.isDebugEnabled()) {
                log.debug("Resolve request finished: biztag={}, steps={}, resultType={}",
                        resultCtx.getBiztag(),
                        resultCtx.getSteps() == null ? 0 : resultCtx.getSteps().size(),
                        result == null ? null : result.getClass().getSimpleName());
            }
            return ApiResult.success(response);
        } catch (Exception e) {
            log.debug("Resolve request failed: biztag={}, error={}", biztag, e.getMessage());
            ResolveResponse response = new ResolveResponse();
            if (ctx != null) {
                response.setSteps(ctx.getSteps());
            }
            return ApiResult.fail(response, e.getMessage());
        } finally {
            contextBuilder.cleanup(ctx);
        }
    }

    /**
     * 异步提交解析任务，并返回可轮询的 taskId。
     */
    public ApiResult<?> submit(String fileId, MultipartFile file, String biztag, String config, String appId, String tenantCode) {
        ResolveContext ctx;
        try {
            if (log.isDebugEnabled()) {
                log.debug("Resolve submit received: biztag={}, appId={}, tenantCode={}, hasFileId={}, hasUploadFile={}, configLength={}",
                        biztag,
                        appId,
                        tenantCode,
                        Strings.isNotBlank(fileId),
                        file != null && !file.isEmpty(),
                        config == null ? 0 : config.length());
            }
            ctx = contextBuilder.build(fileId, file, biztag, config, appId, tenantCode);
            ensureDefaultPrompt(ctx);
        } catch (Exception e) {
            log.debug("Resolve submit build failed: biztag={}, error={}", biztag, e.getMessage());
            return ApiResult.fail(e.getMessage());
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        ctx.setTaskId(taskId);
        if (log.isDebugEnabled()) {
            log.debug("Resolve task created: taskId={}, biztag={}, feature={}, sourceFileName={}",
                    taskId,
                    ctx.getBiztag(),
                    ctx.getFeature(),
                    ctx.getSourceFileName());
        }

        ResolveTask task = new ResolveTask();
        task.setTaskId(taskId);
        task.setStatus(ResolveStatusEnum.PENDING);
        task.setCreatedAt(System.currentTimeMillis());
        task.setUpdatedAt(System.currentTimeMillis());
        task.setBiztag(ctx.getBiztag());
        task.setFeature(ctx.getFeature());
        task.setFileName(ctx.getSourceFileName());
        taskStore.save(task);

        resolveExecutor.execute(() -> {
            ResolveTask running = taskStore.get(taskId);
            if (running == null) {
                running = task;
            }
            running.setStatus(ResolveStatusEnum.RUNNING);
            running.setUpdatedAt(System.currentTimeMillis());
            taskStore.save(running);
            if (log.isDebugEnabled()) {
                log.debug("Resolve task running: taskId={}, biztag={}, feature={}",
                        taskId,
                        ctx.getBiztag(),
                        ctx.getFeature());
            }

            try {
                ResolveContext resultCtx = pipelineRunner.run(ctx);
                Object finalResult = buildSchemaAwareResult(resultCtx);
                running.setStatus(ResolveStatusEnum.SUCCESS);
                running.setUpdatedAt(System.currentTimeMillis());
                running.setSteps(resultCtx.getSteps());
                running.setResult(finalResult);
                taskStore.save(running);
                if (log.isDebugEnabled()) {
                    log.debug("Resolve task success: taskId={}, steps={}, resultType={}",
                            taskId,
                            resultCtx.getSteps() == null ? 0 : resultCtx.getSteps().size(),
                            finalResult == null ? null : finalResult.getClass().getSimpleName());
                }
            } catch (Exception ex) {
                running.setStatus(ResolveStatusEnum.FAILED);
                running.setUpdatedAt(System.currentTimeMillis());
                running.setErrorMsg(ex.getMessage());
                if (ctx != null) {
                    running.setSteps(ctx.getSteps());
                }
                taskStore.save(running);
                log.debug("Resolve task failed: taskId={}, error={}", taskId, ex.getMessage());
            } finally {
                contextBuilder.cleanup(ctx);
            }
        });

        ResolveSubmitResponse response = new ResolveSubmitResponse();
        response.setTaskId(taskId);
        return ApiResult.success(response);
    }

    /**
     * 查询指定异步解析任务的当前状态与结果。
     */
    public ApiResult<?> task(String taskId) {
        ResolveTask task = taskStore.get(taskId);
        if (task == null) {
            log.debug("Resolve task query missed: taskId={}", taskId);
            return ApiResult.fail("task not found");
        }
        if (log.isDebugEnabled()) {
            log.debug("Resolve task query hit: taskId={}, status={}", taskId, task.getStatus());
        }
        return ApiResult.success(task);
    }

    private Object buildSchemaAwareResult(ResolveContext ctx) {
        if (ctx == null || Strings.isBlank(ctx.getBiztag()) || !schemaRegistry.exists(ctx.getBiztag())) {
            return ctx == null ? null : ctx.getResult();
        }

        ResolveBizHandler handler = handlerRegistry.get(ctx.getBiztag());
        if (handler == null) {
            ResolveStepResult step = new ResolveStepResult();
            step.setArtifactId("schema.enforce");
            step.setSuccess(false);
            step.setCostMs(0L);
            step.setErrorMsg("biztag handler not found: " + ctx.getBiztag());
            ctx.getSteps().add(step);
            throw new IllegalStateException(step.getErrorMsg());
        }

        long start = System.currentTimeMillis();
        ExtractedStructuredData extracted = handler.extract(ctx, ctx.getPayload());
        ResolveSchemaEnforceResult enforceResult = schemaEnforcer.enforce(ctx.getBiztag(), extracted);
        if (log.isDebugEnabled()) {
            log.debug("Schema enforced for resolve result: biztag={}, success={}, fixedCount={}, errorCount={}",
                    ctx.getBiztag(),
                    enforceResult.isSuccess(),
                    enforceResult.getFixedCount(),
                    enforceResult.getErrors() == null ? 0 : enforceResult.getErrors().size());
        }

        ResolveStepResult step = new ResolveStepResult();
        step.setArtifactId("schema.enforce");
        step.setSuccess(enforceResult.isSuccess());
        step.setCostMs(System.currentTimeMillis() - start);
        Map<String, Object> output = new HashMap<>();
        output.put("schemaId", enforceResult.getSchemaId());
        output.put("fixedCount", enforceResult.getFixedCount());
        output.put("errorCount", enforceResult.getErrors() == null ? 0 : enforceResult.getErrors().size());
        step.setOutput(output);
        if (!enforceResult.isSuccess() && enforceResult.getErrors() != null && !enforceResult.getErrors().isEmpty()) {
            step.setErrorMsg(enforceResult.getErrors().get(0));
        }
        ctx.getSteps().add(step);

        if (!enforceResult.isSuccess()) {
            throw new IllegalStateException(step.getErrorMsg() == null ? "schema validation failed" : step.getErrorMsg());
        }

        ResolveStandardResult standard = new ResolveStandardResult();
        standard.setBiztag(ctx.getBiztag());
        standard.setSchemaId(enforceResult.getSchemaId());
        standard.setData(enforceResult.getNormalizedData());
        standard.setExtracted(enforceResult.getNormalizedExtracted());
        return standard;
    }

    private void ensureDefaultPrompt(ResolveContext ctx) {
        if (ctx == null || !"carrier.so.parse".equals(ctx.getBiztag())) {
            return;
        }
        if (ctx.getParams() == null) {
            ctx.setParams(new JSONObject());
        }
        if (Strings.isNotBlank(ctx.getParams().getString("prompt"))) {
            return;
        }
        ctx.getParams().put("prompt", carrierSoPromptProvider.defaultPrompt());
        if (ctx.getPayload() != null) {
            ctx.getPayload().put("params", ctx.getParams());
        }
        log.debug("Default carrier.so prompt injected: biztag={}, sourceFileName={}",
                ctx.getBiztag(),
                ctx.getSourceFileName());
    }
}
