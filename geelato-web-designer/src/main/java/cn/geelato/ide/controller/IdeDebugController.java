package cn.geelato.ide.controller;

import cn.geelato.ide.debug.DebugSessionRegistry;
import cn.geelato.ide.dto.IdeDebugRequest;
import cn.geelato.ide.service.IdeDebugDispatcher;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 调试会话同源网关端点（{@code /api/script/debug/*}）。
 * <p>
 * GraalVM Context / CDP inspector 恒在独立 worker JVM 构建，浏览器不能直连内网端口；本控制器作为
 * 与编辑器同源的门面：{@code start} 委托 worker 建会话、换算内网 inspector URI 存 {@link DebugSessionRegistry}
 * 并签发一次性代理 token，只回发 {@code {sessionId, wsProxyPath, devtoolsBase}}（绝不含 host:port）；
 * CDP ws 帧由 {@code ScriptDebugWebSocketProxyHandler} 经 {@code /api/script/debugger/ws/*} 双向转发。
 *
 * @author geelato
 */
@DesignTimeApiRestController("/api/script/debug")
@Slf4j
public class IdeDebugController {

    @Autowired
    private IdeDebugDispatcher debugDispatcher;

    @Autowired
    private DebugSessionRegistry registry;

    /** 自托管 chrome-devtools-frontend 的同源基路径（iframe src 前缀）。 */
    @Value("${geelato.ide.debug.devtoolsBase:/debug-devtools}")
    private String devtoolsBase;

    /**
     * 启动调试会话：委托 worker 建 debug Context（挂起等接），登记内网坐标 + owner + token。
     */
    @PostMapping("/start")
    public ApiResult<?> start(@RequestBody IdeDebugRequest request) {
        if (request == null || Strings.isBlank(request.getContent())) {
            return ApiResult.fail("content 不能为空");
        }
        if (!isJs(request.getLanguage())) {
            return ApiResult.fail("断点调试仅支持 javascript");
        }
        String owner = resolveOwner();
        try {
            IdeDebugDispatcher.DebugTarget target = debugDispatcher.start(request, owner);
            String token = UUID.randomUUID().toString().replace("-", "");
            registry.register(target.getSessionId(), target.inspectorWsUri(), owner, token);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sessionId", target.getSessionId());
            resp.put("wsProxyPath", "/api/script/debugger/ws/" + target.getSessionId() + "?token=" + token);
            resp.put("devtoolsBase", devtoolsBase);
            resp.put("engineVersion", target.getEngineVersion());
            return ApiResult.success(resp);
        } catch (IllegalStateException e) {
            log.warn("debug start 失败: {}", e.getMessage());
            return ApiResult.fail("调试会话启动失败: " + e.getMessage());
        }
    }

    /**
     * 终止调试会话：校验归属后委托 worker 释放端口/关闭 Context，并移除本地登记。
     */
    @PostMapping("/stop/{sessionId}")
    public ApiResult<?> stop(@PathVariable("sessionId") String sessionId) {
        DebugSessionRegistry.Registration reg = registry.get(sessionId);
        if (reg != null && !ownerMatches(reg.getOwner())) {
            return ApiResult.fail(403, "无权终止该调试会话");
        }
        debugDispatcher.stop(sessionId);
        registry.remove(sessionId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("stopped", true);
        return ApiResult.success(resp);
    }

    /**
     * 查询会话状态（校验归属；透传 worker 状态）。
     */
    @GetMapping("/{sessionId}/status")
    public ApiResult<?> status(@PathVariable("sessionId") String sessionId) {
        DebugSessionRegistry.Registration reg = registry.get(sessionId);
        if (reg != null && !ownerMatches(reg.getOwner())) {
            return ApiResult.fail(403, "无权查询该调试会话");
        }
        JSONObject status = debugDispatcher.status(sessionId);
        return ApiResult.success(status);
    }

    private boolean isJs(String language) {
        return Strings.isBlank(language) || "js".equalsIgnoreCase(language) || "javascript".equalsIgnoreCase(language);
    }

    private String resolveOwner() {
        User user = SecurityContext.getCurrentUser();
        return user == null || Strings.isBlank(user.getUserId()) ? "system" : user.getUserId();
    }

    private boolean ownerMatches(String owner) {
        if (owner == null) {
            return true;
        }
        return owner.equals(resolveOwner());
    }
}
