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
import org.graalvm.polyglot.Value;
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

    @org.springframework.beans.factory.annotation.Value("${geelato.script.ext.max-retries:2}")
    private int extMaxRetries;

    @org.springframework.beans.factory.annotation.Value("${geelato.script.ext.retry-interval-ms:0}")
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
                globalGraalVariableMap.put("ctx", Map.of("parameter", resolvedParameter.parsedParameter()));
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
            Value executionResult = context.eval(source).execute(resolvedParameter.parsedParameter());
            Object rawResult = executionResult.hasMembers() && executionResult.hasMember("result")
                    ? executionResult.getMember("result")
                    : null;
            return unwrapPolyglotValue(rawResult);
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
        List<String> lines = new ArrayList<>();
        lines.add(buildHeader(api, attempt, totalAttempts));

        String location = buildSourceLocation(ex);
        if (!StringUtils.isEmpty(location)) {
            lines.add("位置：" + location);
        }

        String readableReason = buildReadableReason(ex);
        if (!StringUtils.isEmpty(readableReason)) {
            lines.add("原因：" + readableReason);
        }

        String scriptSnippet = buildScriptSnippet(ex, scriptContent);
        if (!StringUtils.isEmpty(scriptSnippet)) {
            lines.add("代码：\n" + scriptSnippet);
        }

        String rawDetail = buildRawDetail(ex);
        if (!StringUtils.isEmpty(rawDetail)) {
            lines.add("原始异常：" + rawDetail);
        }
        return String.join("\n", lines);
    }

    private String buildHeader(Api api, int attempt, int totalAttempts) {
        StringBuilder builder = new StringBuilder("脚本执行失败");
        if (api != null && !StringUtils.isEmpty(api.getCode())) {
            builder.append("，脚本编码：").append(api.getCode());
        }
        builder.append("，执行次数：").append(attempt).append("/").append(totalAttempts);
        return builder.toString();
    }

    private String buildSourceLocation(Exception ex) {
        if (!(ex instanceof PolyglotException polyglotException)) {
            return "";
        }
        SourceSection sourceLocation = polyglotException.getSourceLocation();
        if (sourceLocation == null) {
            return "";
        }
        return String.format("%s 第%s行，第%s列", sourceLocation.getSource().getName(), sourceLocation.getStartLine(), sourceLocation.getStartColumn());
    }

    private String buildReadableReason(Exception ex) {
        if (!(ex instanceof PolyglotException polyglotException)) {
            return ex == null ? "" : safeMessage(ex.getMessage());
        }
        String rawMessage = safeMessage(polyglotException.getMessage());
        if (polyglotException.isSyntaxError()) {
            return "JavaScript 语法错误：" + rawMessage;
        }
        if (polyglotException.isGuestException()) {
            return rawMessage;
        }
        if (polyglotException.isHostException()) {
            return "脚本调用 Java 服务时报错：" + rawMessage;
        }
        return rawMessage;
    }

    private String buildRawDetail(Exception ex) {
        if (ex == null) {
            return "";
        }
        if (ex instanceof PolyglotException polyglotException) {
            List<String> details = new ArrayList<>();
            String type = "";
            if (polyglotException.isSyntaxError()) {
                type = "syntax";
            } else if (polyglotException.isGuestException()) {
                type = "guest";
            } else if (polyglotException.isHostException()) {
                type = "host";
            }
            if (!StringUtils.isEmpty(type)) {
                details.add("type=" + type);
            }
            String rawMessage = safeMessage(polyglotException.getMessage());
            if (!StringUtils.isEmpty(rawMessage)) {
                details.add("message=" + rawMessage);
            }
            String guestStack = buildGuestStack(polyglotException);
            if (!StringUtils.isEmpty(guestStack)) {
                details.add("guestStack=" + guestStack);
            }
            return String.join(", ", details);
        }
        return safeMessage(ex.getMessage());
    }

    private String buildGuestStack(PolyglotException ex) {
        List<String> frames = new ArrayList<>();
        for (PolyglotException.StackFrame stackFrame : ex.getPolyglotStackTrace()) {
            if (stackFrame.isGuestFrame()) {
                frames.add(stackFrame.toString());
            }
            if (frames.size() >= 5) {
                break;
            }
        }
        return frames.isEmpty() ? "" : String.join(" | ", frames);
    }

    private String buildScriptSnippet(Exception ex, String scriptContent) {
        if (!(ex instanceof PolyglotException polyglotException)) {
            return "";
        }
        SourceSection sourceLocation = polyglotException.getSourceLocation();
        if (sourceLocation == null) {
            return "";
        }
        String[] lines = scriptContent.split("\\R", -1);
        int targetLine = sourceLocation.getStartLine();
        if (targetLine <= 0 || targetLine > lines.length) {
            return "";
        }
        int startLine = Math.max(1, targetLine - 2);
        int endLine = Math.min(lines.length, targetLine + 2);
        List<String> snippetLines = new ArrayList<>();
        for (int lineIndex = startLine; lineIndex <= endLine; lineIndex++) {
            String prefix = lineIndex == targetLine ? ">> " : "   ";
            String linePrefix = prefix + lineIndex + " | ";
            snippetLines.add(linePrefix + lines[lineIndex - 1]);
            if (lineIndex == targetLine) {
                snippetLines.add(buildPointerLine(linePrefix.length(), sourceLocation.getStartColumn(), sourceLocation.getEndColumn()));
            }
        }
        return String.join("\n", snippetLines);
    }

    private String buildPointerLine(int prefixLength, int startColumn, int endColumn) {
        int safeStart = Math.max(startColumn, 1);
        int safeEnd = Math.max(endColumn, safeStart);
        StringBuilder builder = new StringBuilder();
        builder.append(" ".repeat(Math.max(0, prefixLength)));
        builder.append(" ".repeat(safeStart - 1));
        int markLength = Math.max(1, safeEnd - safeStart + 1);
        builder.append("^".repeat(markLength));
        return builder.toString();
    }

    private String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\r", " ").replace("\n", " ").trim();
    }

    private Object unwrapPolyglotValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Value polyglotValue) {
            return unwrapValue(polyglotValue);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> javaMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                javaMap.put(String.valueOf(entry.getKey()), unwrapPolyglotValue(entry.getValue()));
            }
            return javaMap;
        }
        if (value instanceof List<?> list) {
            List<Object> javaList = new ArrayList<>();
            for (Object item : list) {
                javaList.add(unwrapPolyglotValue(item));
            }
            return javaList;
        }
        return value;
    }

    private Object unwrapValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.fitsInInt()) {
            return value.asInt();
        }
        if (value.fitsInLong()) {
            return value.asLong();
        }
        if (value.fitsInFloat()) {
            return value.asFloat();
        }
        if (value.fitsInDouble()) {
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> javaList = new ArrayList<>();
            long size = value.getArraySize();
            for (long index = 0; index < size; index++) {
                javaList.add(unwrapValue(value.getArrayElement(index)));
            }
            return javaList;
        }
        if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            return unwrapPolyglotValue(hostObject);
        }
        if (value.hasMembers()) {
            Map<String, Object> javaMap = new HashMap<>();
            for (String memberKey : value.getMemberKeys()) {
                javaMap.put(memberKey, unwrapValue(value.getMember(memberKey)));
            }
            return javaMap;
        }
        return value.toString();
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

    private record ResolvedParameter(Object parsedParameter, boolean hasParameter) {
    }
}
