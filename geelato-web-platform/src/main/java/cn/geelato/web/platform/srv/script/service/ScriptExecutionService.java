package cn.geelato.web.platform.srv.script.service;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.meta.Api;
import cn.geelato.utils.JsonUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.graal.GraalContext;
import cn.geelato.web.platform.graal.GraalExecutor;
import cn.geelato.web.platform.srv.script.GraalUse;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ScriptExecutionService {
    private final GraalManager graalManager = GraalManager.singleInstance();
    private final ApiService apiService;

    @Value("${geelato.script.ext.max-retries:2}")
    private int extMaxRetries;

    @Value("${geelato.script.ext.retry-interval-ms:0}")
    private long extRetryIntervalMs;

    public ScriptExecutionService(ApiService apiService) {
        this.apiService = apiService;
    }

    public ScriptExecutionResult executeInternalById(String scriptId, Object parameter) {
        Api api = apiService.getModel(Api.class, scriptId);
        if (api == null) {
            throw new ScriptExecutionException("not found script, scriptId=" + scriptId, null, null, false);
        }
        return executeApi(api, parameter, 0);
    }

    public ScriptExecutionResult executeExternalByOutsideUrl(String outsideUrl, Object parameter) {
        Api api = getApiByOutsideUrl(outsideUrl);
        if (api == null) {
            throw new ScriptExecutionException("not found script, outsideUrl=/" + outsideUrl, null, null, false);
        }
        return executeApi(api, parameter, Math.max(extMaxRetries, 0));
    }

    public Object executeByCode(String code, Object parameter) {
        Api api = getApiByCode(code);
        if (api == null) {
            throw new ScriptExecutionException("not found script, code=" + code, null, null, false);
        }
        return executeApi(api, parameter, 0).getResult();
    }

    public ScriptExecutionResult executeApi(Api api, Object parameter, int maxRetries) {
        String scriptContent = buildScriptContent(api);
        ResolvedParameter resolvedParameter = resolveParameter(parameter);
        int totalAttempts = Math.max(maxRetries, 0) + 1;
        Exception lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                Object result = runScript(api, scriptContent, resolvedParameter);
                return new ScriptExecutionResult(api, result, attempt);
            } catch (Exception ex) {
                lastException = ex;
                boolean retryable = isRetryable(ex);
                if (!retryable || attempt >= totalAttempts) {
                    throw buildScriptExecutionException(api, ex, scriptContent, attempt, totalAttempts, retryable);
                }
                log.warn("script execute failed, apiCode={}, attempt={}/{}, retrying: {}", api.getCode(), attempt, totalAttempts, ex.getMessage());
                sleepBeforeRetry();
            }
        }

        throw buildScriptExecutionException(api, lastException, scriptContent, totalAttempts, totalAttempts, false);
    }

    private Object runScript(Api api, String scriptContent, ResolvedParameter resolvedParameter) throws IOException {
        try (Context context = GraalContext.getContext()) {
            Map<String, Object> graalServiceMap = graalManager.getGraalServiceMap();
            Map<String, Object> graalVariableMap = graalManager.getGraalVariableMap();
            Map<String, Object> globalGraalVariableMap = new HashMap<>(graalManager.getGlobalGraalVariableMap());
            globalGraalVariableMap.remove("ctx");
            if (resolvedParameter.hasParameter()) {
                globalGraalVariableMap.put("ctx", Map.of("parameter", resolvedParameter.getParsedParameter()));
            }

            context.getBindings(GraalUse.Language_JS).putMember(GraalUse.GLOBAL_OBJECT, globalGraalVariableMap);
            for (Map.Entry<String, Object> entry : graalServiceMap.entrySet()) {
                context.getBindings(GraalUse.Language_JS).putMember(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Object> entry : graalVariableMap.entrySet()) {
                context.getBindings(GraalUse.Language_JS).putMember(entry.getKey(), entry.getValue());
            }
            context.getBindings(GraalUse.Language_JS).putMember(GraalUse.GLOBAL_EXECUTOR, new GraalExecutor(this));

            Source source = Source.newBuilder(GraalUse.Language_JS, scriptContent, buildSourceName(api)).build();
            Map<?, ?> result = context.eval(source).execute(resolvedParameter.getParsedParameter()).as(Map.class);
            return result.get("result");
        }
    }

    private String buildScriptContent(Api api) {
        return GraalUse.BASE_SCRIPT_CONTENT.replace(GraalUse.CUSTOM_CONTENT_TAG, api.getReleaseContent());
    }

    private String buildSourceName(Api api) {
        String sourceName = api.getCode();
        if (StringUtils.isEmpty(sourceName)) {
            sourceName = "script";
        }
        return sourceName + ".js";
    }

    private Api getApiByOutsideUrl(String outsideUrl) {
        Map<String, Object> params = new HashMap<>();
        params.put("outsideUrl", "/" + outsideUrl);
        List<Api> apiList = apiService.queryModel(Api.class, params);
        if (apiList == null || apiList.isEmpty()) {
            return null;
        }
        return apiList.get(0);
    }

    private Api getApiByCode(String code) {
        List<Api> apiList = apiService.queryModel(Api.class, Map.of("code", code));
        if (apiList == null || apiList.isEmpty()) {
            return null;
        }
        return apiList.get(0);
    }

    private ResolvedParameter resolveParameter(Object parameter) {
        if (parameter == null) {
            return new ResolvedParameter(null, false);
        }
        if (parameter instanceof String stringParameter) {
            if (StringUtils.isEmpty(stringParameter)) {
                return new ResolvedParameter(JsonUtils.safeParse(stringParameter), false);
            }
            return new ResolvedParameter(JsonUtils.safeParse(stringParameter), true);
        }
        return new ResolvedParameter(parameter, true);
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof ScriptExecutionException scriptExecutionException) {
            return scriptExecutionException.isRetryable();
        }
        if (ex instanceof PolyglotException polyglotException) {
            return !polyglotException.isSyntaxError() && !polyglotException.isExit() && !polyglotException.isCancelled();
        }
        return true;
    }

    private ScriptExecutionException buildScriptExecutionException(Api api, Exception ex, String scriptContent, int attempt, int totalAttempts, boolean retryable) {
        String message = buildErrorMessage(api, ex, scriptContent, attempt, totalAttempts);
        return new ScriptExecutionException(message, ex, api, retryable);
    }

    private String buildErrorMessage(Api api, Exception ex, String scriptContent, int attempt, int totalAttempts) {
        StringBuilder builder = new StringBuilder("script execute failed");
        if (api != null && !StringUtils.isEmpty(api.getCode())) {
            builder.append(", code=").append(api.getCode());
        }
        builder.append(", attempt=").append(attempt).append("/").append(totalAttempts);

        if (ex instanceof PolyglotException polyglotException) {
            if (polyglotException.isSyntaxError()) {
                builder.append(", type=syntax");
            } else if (polyglotException.isGuestException()) {
                builder.append(", type=guest");
            } else if (polyglotException.isHostException()) {
                builder.append(", type=host");
            }
            appendSourceLocation(builder, polyglotException);
            appendGuestStack(builder, polyglotException);
            builder.append(", message=").append(safeMessage(polyglotException.getMessage()));
        } else if (ex != null) {
            builder.append(", message=").append(safeMessage(ex.getMessage()));
        }

        appendScriptSnippet(builder, ex, scriptContent);
        return builder.toString();
    }

    private void appendSourceLocation(StringBuilder builder, PolyglotException ex) {
        SourceSection sourceLocation = ex.getSourceLocation();
        if (sourceLocation == null) {
            return;
        }
        builder.append(", file=").append(sourceLocation.getSource().getName());
        builder.append(", line=").append(sourceLocation.getStartLine());
        builder.append(", column=").append(sourceLocation.getStartColumn());
        builder.append(", endLine=").append(sourceLocation.getEndLine());
        builder.append(", endColumn=").append(sourceLocation.getEndColumn());
    }

    private void appendGuestStack(StringBuilder builder, PolyglotException ex) {
        List<String> frames = new ArrayList<>();
        for (PolyglotException.StackFrame stackFrame : ex.getPolyglotStackTrace()) {
            if (stackFrame.isGuestFrame()) {
                frames.add(stackFrame.toString());
            }
            if (frames.size() >= 5) {
                break;
            }
        }
        if (!frames.isEmpty()) {
            builder.append(", guestStack=").append(String.join(" | ", frames));
        }
    }

    private void appendScriptSnippet(StringBuilder builder, Exception ex, String scriptContent) {
        if (!(ex instanceof PolyglotException polyglotException)) {
            return;
        }
        SourceSection sourceLocation = polyglotException.getSourceLocation();
        if (sourceLocation == null) {
            return;
        }
        String[] lines = scriptContent.split("\\R", -1);
        int targetLine = sourceLocation.getStartLine();
        if (targetLine <= 0 || targetLine > lines.length) {
            return;
        }
        int startLine = Math.max(1, targetLine - 2);
        int endLine = Math.min(lines.length, targetLine + 2);
        builder.append(", snippet=");
        for (int lineIndex = startLine; lineIndex <= endLine; lineIndex++) {
            if (lineIndex > startLine) {
                builder.append(" || ");
            }
            if (lineIndex == targetLine) {
                builder.append(">>");
            }
            builder.append(lineIndex).append(": ").append(lines[lineIndex - 1].trim());
        }
    }

    private String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\r", " ").replace("\n", " ").trim();
    }

    private void sleepBeforeRetry() {
        if (extRetryIntervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(extRetryIntervalMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ResolvedParameter {
        private final Object parsedParameter;
        private final boolean hasParameter;

        private ResolvedParameter(Object parsedParameter, boolean hasParameter) {
            this.parsedParameter = parsedParameter;
            this.hasParameter = hasParameter;
        }

        public Object getParsedParameter() {
            return parsedParameter;
        }

        public boolean hasParameter() {
            return hasParameter;
        }
    }
}
