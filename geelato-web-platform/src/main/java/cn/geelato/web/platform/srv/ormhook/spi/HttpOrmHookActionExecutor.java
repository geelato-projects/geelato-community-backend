package cn.geelato.web.platform.srv.ormhook.spi;

import cn.geelato.meta.OrmHook;
import cn.geelato.utils.HttpUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * http 动作执行器（v3 定案：Hook 只配地址）。
 * <p>
 * 固定 POST 完整载荷 JSON 至 {@code platform_orm_hook.http_url}，
 * Content-Type: application/json，方法/请求头/请求体均不可配（"只配地址"）。
 * 载荷即下游分发所需三要素：entityName（实体名称）、eventType（insert/update/delete）、
 * values（数据变更明细：本次写入字段快照；删除时为删除条件+affectedRows），
 * 另附 tableName/eventId/session/firedAt。
 * <p>
 * 判定：HTTP 2xx 为成功；非 2xx、IO 异常为失败（进重试/死信）。
 * 客户端复用 {@link HttpUtils} 的 OkHttp 实例（默认超时：连接/读/写各 10s）。
 * 内网定位；调用外部地址的 SSRF 面与平台既有 GraalJS http 服务一致。
 *
 * @author geelato
 */
@Component
@Slf4j
public class HttpOrmHookActionExecutor implements OrmHookActionExecutor {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Override
    public String getType() {
        return TYPE_HTTP;
    }

    @Override
    public OrmHookActionResult execute(OrmHook rule, Map<String, Object> payload) {
        try {
            Request request = new Request.Builder()
                    .url(rule.getHttpUrl())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(JSON.toJSONString(payload), JSON_MEDIA_TYPE))
                    .build();
            OkHttpClient client = HttpUtils.getUnsafeOkHttpClient();
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String respBody = response.body() != null ? response.body().string() : "";
                if (code >= 200 && code < 300) {
                    if (log.isDebugEnabled()) {
                        log.debug("ORM Hook 执行完成 hookId={}, {} -> {} {}", rule.getId(), rule.getHttpUrl(), code, abbreviate(respBody));
                    }
                    return OrmHookActionResult.success();
                }
                String err = String.format("HTTP %d: %s", code, abbreviate(respBody));
                log.warn("ORM Hook 执行失败 hookId={}, {} -> {}", rule.getId(), rule.getHttpUrl(), err);
                return OrmHookActionResult.fail(err);
            }
        } catch (Exception e) {
            log.warn("ORM Hook 执行异常 hookId={}, {}: {}", rule.getId(), rule.getHttpUrl(), e.getMessage());
            return OrmHookActionResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
