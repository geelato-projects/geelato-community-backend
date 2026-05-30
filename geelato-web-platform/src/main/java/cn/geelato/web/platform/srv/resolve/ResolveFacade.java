package cn.geelato.web.platform.srv.resolve;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.integration.ResolvePipelineResolver;
import cn.geelato.web.platform.resolve.model.ResolveResponse;
import cn.geelato.web.platform.resolve.model.ResolveStatusEnum;
import cn.geelato.web.platform.resolve.model.ResolveSubmitResponse;
import cn.geelato.web.platform.resolve.model.ResolveTask;
import cn.geelato.web.platform.resolve.task.ResolveTaskStore;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.integration.core.MessagingTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class ResolveFacade {
    private final FileHandler fileHandler;
    private final ResolvePipelineResolver pipelineResolver;
    private final ResolveTaskStore taskStore;
    private final Map<String, MessageChannel> channels;
    private final Executor resolveExecutor;

    public ResolveFacade(
            FileHandler fileHandler,
            ResolvePipelineResolver pipelineResolver,
            ResolveTaskStore taskStore,
            Map<String, MessageChannel> channels,
            @Qualifier("resolveExecutor") Executor resolveExecutor
    ) {
        this.fileHandler = fileHandler;
        this.pipelineResolver = pipelineResolver;
        this.taskStore = taskStore;
        this.channels = channels;
        this.resolveExecutor = resolveExecutor;
    }

    public ApiResult<?> resolve(String fileId, MultipartFile file, String biztag, String payload, String appId, String tenantCode) {
        ResolveContext ctx = null;
        try {
            ctx = buildContext(fileId, file, biztag, payload, appId, tenantCode);
            ResolveContext resultCtx = doResolve(ctx);
            ResolveResponse response = new ResolveResponse();
            response.setResult(resultCtx.getResult());
            response.setSteps(resultCtx.getSteps());
            return ApiResult.success(response);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        } finally {
            cleanup(ctx);
        }
    }

    public ApiResult<?> submit(String fileId, MultipartFile file, String biztag, String payload, String appId, String tenantCode) {
        ResolveContext ctx;
        try {
            ctx = buildContext(fileId, file, biztag, payload, appId, tenantCode);
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
                ResolveContext resultCtx = doResolve(ctx);
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
                cleanup(ctx);
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

    private ResolveContext doResolve(ResolveContext ctx) {
        String channelName = pipelineResolver.resolveChannelName(ctx);
        MessageChannel channel = channels.get(channelName);
        if (channel == null) {
            throw new IllegalStateException("resolve pipeline not found: " + channelName);
        }

        MessagingTemplate template = new MessagingTemplate();
        template.setReceiveTimeout(180000L);
        Message<?> reply = template.sendAndReceive(channel, MessageBuilder.withPayload(ctx).build());
        if (reply == null) {
            throw new IllegalStateException("resolve pipeline timeout");
        }
        return (ResolveContext) reply.getPayload();
    }

    private ResolveContext buildContext(String fileId, MultipartFile multipartFile, String biztag, String payload, String appId, String tenantCode) throws IOException {
        if (Strings.isBlank(fileId) && (multipartFile == null || multipartFile.isEmpty())) {
            throw new IllegalArgumentException("fileId or file is required");
        }

        ResolveContext ctx = new ResolveContext();
        ctx.setAppId(appId);
        ctx.setTenantCode(tenantCode);

        File sourceFile;
        String sourceFileName;

        if (Strings.isNotBlank(fileId)) {
            Attachment attachment = fileHandler.getAttachment(fileId);
            if (attachment == null) {
                throw new IllegalArgumentException("file not found");
            }
            sourceFileName = attachment.getName();
            sourceFile = fileHandler.toFile(attachment);
            if (sourceFile == null || !sourceFile.exists()) {
                throw new IllegalArgumentException("file not found");
            }
            if (Strings.isNotBlank(attachment.getObjectId())) {
                ctx.getTempFiles().add(sourceFile);
            }
        } else {
            sourceFileName = multipartFile.getOriginalFilename();
            if (Strings.isBlank(sourceFileName)) {
                sourceFileName = "upload_" + System.currentTimeMillis();
            }
            String suffix = "";
            int dot = sourceFileName.lastIndexOf('.');
            if (dot >= 0) {
                suffix = sourceFileName.substring(dot);
            }
            sourceFile = Files.createTempFile("resolve_upload_", suffix).toFile();
            multipartFile.transferTo(sourceFile);
            ctx.getTempFiles().add(sourceFile);
        }

        ctx.setSourceFile(sourceFile);
        ctx.setSourceFileName(sourceFileName);
        ctx.setSourceExt(extractExt(sourceFileName));

        JSONObject payloadObj = new JSONObject();
        if (Strings.isNotBlank(payload)) {
            payloadObj = JSON.parseObject(payload);
        }
        ctx.setPayload(payloadObj);
        ctx.setBiztag(biztag);
        String feature = Strings.isNotBlank(biztag) ? biztag : payloadObj.getString("feature");
        ctx.setFeature(feature);
        ctx.setParams(payloadObj.getJSONObject("params"));
        ctx.setWorkflow(payloadObj.getJSONObject("workflow"));
        ctx.setTrace(payloadObj.getJSONObject("trace"));
        return ctx;
    }

    private String extractExt(String fileName) {
        if (Strings.isBlank(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return "." + fileName.substring(dot + 1).toUpperCase();
    }

    private void cleanup(ResolveContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(ctx.getTempFiles())) {
            return;
        }
        for (File file : ctx.getTempFiles()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) {
            }
        }
    }
}
