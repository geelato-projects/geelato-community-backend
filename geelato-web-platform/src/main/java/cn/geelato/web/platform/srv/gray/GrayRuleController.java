package cn.geelato.web.platform.srv.gray;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.gray.model.GrayRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度规则管理接口：仅提供内存规则的查看与刷新。
 * <p>
 * 规则 JSON 由运维直接写入 Redis（key 由 {@code geelato.traffic.gray.redis-key} 决定，默认
 * {@code geelato:gray:rules}），修改后调用 {@code /reload} 即时生效。
 */
@ApiRestController("/gray-rules")
@Slf4j
public class GrayRuleController extends BaseController {

    private final GrayRuleMatcher matcher;

    @Autowired
    public GrayRuleController(GrayRuleMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * 重新从 Redis 加载规则到内存。
     * <p>
     * 返回 {@code count} 为加载后的规则条数。
     */
    @RequestMapping(value = "/reload", method = RequestMethod.POST)
    public ApiResult<Map<String, Object>> reload() {
        try {
            int count = matcher.reload();
            Map<String, Object> data = new HashMap<>();
            data.put("count", count);
            return ApiResult.success(data);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 查看当前内存中的规则，便于运维核对。
     */
    @RequestMapping(value = "/get", method = RequestMethod.GET)
    public ApiResult<List<GrayRule>> get() {
        try {
            return ApiResult.success(matcher.currentRules());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
