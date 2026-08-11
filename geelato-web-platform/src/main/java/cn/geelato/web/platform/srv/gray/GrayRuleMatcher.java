package cn.geelato.web.platform.srv.gray;

import cn.geelato.security.User;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.gray.model.GrayRule;
import cn.geelato.web.common.traffic.JsonGrayRuleProperties;
import cn.geelato.web.common.traffic.TrafficColoringProperties;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 灰度规则匹配器：从 Redis 加载 JSON 规则到内存，按当前登录用户匹配，返回灰度标签。
 * <p>
 * 规则 JSON 为数组，存放于 {@link JsonGrayRuleProperties#getRedisKey()}。加载失败或为空时，
 * 视为空规则（全部走 default），保证安全降级。调用 {@link #reload()} 可即时刷新。
 */
@Slf4j
@Component
public class GrayRuleMatcher {

    private final StringRedisTemplate redisTemplate;
    private final JsonGrayRuleProperties properties;
    private final TrafficColoringProperties trafficColoringProperties;

    private volatile List<GrayRule> rules = Collections.emptyList();

    @Autowired
    public GrayRuleMatcher(StringRedisTemplate redisTemplate,
                           JsonGrayRuleProperties properties,
                           TrafficColoringProperties trafficColoringProperties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.trafficColoringProperties = trafficColoringProperties;
    }

    @PostConstruct
    public void init() {
        load();
    }

    /**
     * 从 Redis 重新加载规则到内存。
     *
     * @return 加载后的规则条数
     */
    public int reload() {
        load();
        return rules.size();
    }

    /**
     * 返回当前内存中的规则（不可变副本），供管理接口核对。
     */
    public List<GrayRule> currentRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * 根据当前用户匹配灰度规则。
     *
     * @param user 当前登录用户，可为 null（未登录）
     * @return 命中规则的 grayTag，否则返回 defaultTag
     */
    public String resolve(User user) {
        if (!properties.isEnabled() || user == null) {
            return defaultTag();
        }
        List<GrayRule> snapshot = rules;
        if (snapshot.isEmpty()) {
            return defaultTag();
        }
        for (GrayRule rule : snapshot) {
            if (matches(rule, user)) {
                String tag = rule.getGrayTag();
                return StringUtils.isNotEmpty(tag) ? tag.trim() : grayTag();
            }
        }
        return defaultTag();
    }

    private void load() {
        try {
            String json = redisTemplate.opsForValue().get(properties.getRedisKey());
            if (StringUtils.isEmpty(json)) {
                rules = Collections.emptyList();
                log.info("gray rules reloaded: 0 (empty or not set, key={})", properties.getRedisKey());
                return;
            }
            GrayRule[] arr = JSON.parseObject(json, GrayRule[].class, JSONReader.Feature.SupportSmartMatch);
            List<GrayRule> list = (arr == null || arr.length == 0)
                    ? Collections.emptyList()
                    : new ArrayList<>(Arrays.asList(arr));
            list.sort(Comparator.comparingInt(
                    (GrayRule r) -> r.getPriority() == null ? 0 : r.getPriority()).reversed());
            rules = Collections.unmodifiableList(list);
            log.info("gray rules reloaded: {} (key={})", list.size(), properties.getRedisKey());
        } catch (Exception e) {
            // 安全降级：解析失败不阻断请求，全部走 default
            rules = Collections.emptyList();
            log.warn("load gray rules failed, fallback to empty (key={})", properties.getRedisKey(), e);
        }
    }

    private boolean matches(GrayRule rule, User user) {
        if (rule == null || StringUtils.isEmpty(rule.getTargetType())) {
            return false;
        }
        String type = rule.getTargetType().trim().toUpperCase();
        String value = rule.getTargetValue();
        switch (type) {
            case "USER":
                return containsValue(value, user.getUserId(), user.getLoginName());
            case "ORG":
                return containsValue(value, user.getOrgId());
            case "DEPT":
                return containsValue(value, user.getDeptId());
            case "BU":
                return containsValue(value, user.getBuId());
            case "TENANT":
                return containsValue(value, user.getTenantCode());
            case "PERCENT":
                return matchPercent(value, user.getUserId());
            case "ALL":
                return true;
            default:
                return false;
        }
    }

    private boolean containsValue(String configValue, String... candidates) {
        if (StringUtils.isEmpty(configValue)) {
            return false;
        }
        Set<String> targets = splitToSet(configValue);
        for (String c : candidates) {
            if (StringUtils.isNotEmpty(c) && targets.contains(c.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchPercent(String configValue, String userId) {
        if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(configValue)) {
            return false;
        }
        int percent;
        try {
            percent = Integer.parseInt(configValue.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }
        String salt = properties.getHashSalt();
        String mat = (StringUtils.isEmpty(salt) ? "" : salt) + userId;
        int bucket = Math.floorMod(mat.hashCode(), 100);
        return bucket < percent;
    }

    private Set<String> splitToSet(String configValue) {
        Set<String> set = new HashSet<>();
        for (String part : configValue.split(",")) {
            if (StringUtils.isNotEmpty(part)) {
                set.add(part.trim());
            }
        }
        return set;
    }

    private String defaultTag() {
        String v = trafficColoringProperties.getDefaultTag();
        return StringUtils.isNotEmpty(v) ? v.trim() : "default";
    }

    private String grayTag() {
        String v = trafficColoringProperties.getGrayTag();
        return StringUtils.isNotEmpty(v) ? v.trim() : "gray";
    }
}
