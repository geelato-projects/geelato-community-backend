package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.model.ResolveResponse;
import cn.geelato.web.platform.resolve.model.ResolveStatusEnum;
import cn.geelato.web.platform.resolve.model.ResolveSubmitResponse;
import cn.geelato.web.platform.resolve.model.ResolveTask;
import cn.geelato.web.platform.resolve.task.ResolveTaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class ResolveFacade {
    private final ResolveContextBuilder contextBuilder;
    private final ResolvePipelineRunner pipelineRunner;
    private final ResolveTaskStore taskStore;
    private final Executor resolveExecutor;

    public ResolveFacade(
            ResolveContextBuilder contextBuilder,
            ResolvePipelineRunner pipelineRunner,
            ResolveTaskStore taskStore,
            @Qualifier("resolveExecutor") Executor resolveExecutor
    ) {
        this.contextBuilder = contextBuilder;
        this.pipelineRunner = pipelineRunner;
        this.taskStore = taskStore;
        this.resolveExecutor = resolveExecutor;
    }

    public ApiResult<?> resolve(String fileId, MultipartFile file, String biztag, String config, String appId, String tenantCode) {
        ResolveContext ctx = null;
        try {
            ctx = contextBuilder.build(fileId, file, biztag, config, appId, tenantCode);
            ResolveContext resultCtx = pipelineRunner.run(ctx);
            ResolveResponse response = new ResolveResponse();
            response.setResult(resultCtx.getResult());
            response.setSteps(resultCtx.getSteps());
            return ApiResult.success(response);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        } finally {
            contextBuilder.cleanup(ctx);
        }
    }

    public ApiResult<?> submit(String fileId, MultipartFile file, String biztag, String config, String appId, String tenantCode) {
        ResolveContext ctx;
        try {
            ctx = contextBuilder.build(fileId, file, biztag, config, appId, tenantCode);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        ctx.setTaskId(taskId);

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

            try {
                ResolveContext resultCtx = pipelineRunner.run(ctx);
                running.setStatus(ResolveStatusEnum.SUCCESS);
                running.setUpdatedAt(System.currentTimeMillis());
                running.setSteps(resultCtx.getSteps());
                running.setResult(resultCtx.getResult());
                taskStore.save(running);
            } catch (Exception ex) {
                running.setStatus(ResolveStatusEnum.FAILED);
                running.setUpdatedAt(System.currentTimeMillis());
                running.setErrorMsg(ex.getMessage());
                if (ctx != null) {
                    running.setSteps(ctx.getSteps());
                }
                taskStore.save(running);
            } finally {
                contextBuilder.cleanup(ctx);
            }
        });

        ResolveSubmitResponse response = new ResolveSubmitResponse();
        response.setTaskId(taskId);
        return ApiResult.success(response);
    }

    public ApiResult<?> task(String taskId) {
        ResolveTask task = taskStore.get(taskId);
        if (task == null) {
            return ApiResult.fail("task not found");
        }
        return ApiResult.success(task);
    }
}
