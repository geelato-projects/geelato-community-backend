package cn.geelato.web.platform.srv.security.service;

import cn.geelato.security.User;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.online.OnlineUserTracker;
import cn.geelato.web.platform.srv.security.entity.OnlineUserInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisOnlineUserTracker implements OnlineUserTracker {
    private static final String DEFAULT_PREFIX = "geelato:online";
    private static final int DEFAULT_WINDOW_MINUTES = 10;
    private static final int DEFAULT_MAX_RETURN = 2000;

    private final StringRedisTemplate redisTemplate;
    private final Environment environment;

    @Autowired
    public RedisOnlineUserTracker(StringRedisTemplate redisTemplate, Environment environment) {
        this.redisTemplate = redisTemplate;
        this.environment = environment;
    }

    @Override
    @Async("eventExecutor")
    public void touch(User user, HttpServletRequest request) {
        if (user == null || StringUtils.isEmpty(user.getTenantCode()) || StringUtils.isEmpty(user.getUserId())) {
            return;
        }
        String member = member(user.getTenantCode(), user.getUserId());
        long now = System.currentTimeMillis();
        long windowMillis = getWindowMinutes(null) * 60_000L;
        long expiredAt = now - windowMillis;
        long removeMax = expiredAt - 1;
        String indexKey = indexKey();
        String userKey = userKey(user.getTenantCode(), user.getUserId());
        try {
            if (removeMax > 0) {
                redisTemplate.opsForZSet().removeRangeByScore(indexKey, 0, removeMax);
            }
            redisTemplate.opsForZSet().add(indexKey, member, (double) now);

            redisTemplate.opsForHash().put(userKey, "userId", user.getUserId());
            redisTemplate.opsForHash().put(userKey, "loginName", nullToEmpty(user.getLoginName()));
            redisTemplate.opsForHash().put(userKey, "userName", nullToEmpty(user.getUserName()));
            redisTemplate.opsForHash().put(userKey, "tenantCode", nullToEmpty(user.getTenantCode()));
            redisTemplate.opsForHash().put(userKey, "orgId", nullToEmpty(user.getOrgId()));
            redisTemplate.opsForHash().put(userKey, "orgName", nullToEmpty(user.getOrgName()));
            redisTemplate.opsForHash().put(userKey, "deptId", nullToEmpty(user.getDeptId()));
            redisTemplate.opsForHash().put(userKey, "buId", nullToEmpty(user.getBuId()));
            redisTemplate.opsForHash().put(userKey, "lastSeen", String.valueOf(now));

            int ttlMinutes = Math.max(getWindowMinutes(null) * 2, 1);
            redisTemplate.expire(userKey, ttlMinutes, TimeUnit.MINUTES);
            redisTemplate.expire(indexKey, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception ex) {
            log.debug("touch online user failed", ex);
        }
    }

    public List<OnlineUserInfo> listOnline(Integer windowMinutes, Integer limit) {
        long now = System.currentTimeMillis();
        int window = getWindowMinutes(windowMinutes);
        int maxReturn = getMaxReturn(limit);
        long expiredAt = now - window * 60_000L;
        long removeMax = expiredAt - 1;
        String indexKey = indexKey();
        try {
            if (removeMax > 0) {
                redisTemplate.opsForZSet().removeRangeByScore(indexKey, 0, removeMax);
            }
            Set<String> members = redisTemplate.opsForZSet().reverseRangeByScore(indexKey, expiredAt, now, 0, maxReturn);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<OnlineUserInfo> result = new ArrayList<>(members.size());
            for (String m : members) {
                if (StringUtils.isEmpty(m)) {
                    continue;
                }
                ParsedMember parsed = parseMember(m);
                if (parsed == null) {
                    continue;
                }
                String userKey = userKey(parsed.tenantCode, parsed.userId);
                Map<Object, Object> map = redisTemplate.opsForHash().entries(userKey);
                if (map == null || map.isEmpty()) {
                    redisTemplate.opsForZSet().remove(indexKey, m);
                    continue;
                }
                OnlineUserInfo info = new OnlineUserInfo();
                info.setUserId(stringValue(map.get("userId"), parsed.userId));
                info.setLoginName(stringValue(map.get("loginName"), null));
                info.setUserName(stringValue(map.get("userName"), null));
                info.setTenantCode(stringValue(map.get("tenantCode"), parsed.tenantCode));
                info.setOrgId(stringValue(map.get("orgId"), null));
                info.setOrgName(stringValue(map.get("orgName"), null));
                info.setDeptId(stringValue(map.get("deptId"), null));
                info.setBuId(stringValue(map.get("buId"), null));
                info.setLastSeen(longValue(map.get("lastSeen")));
                result.add(info);
            }
            return result;
        } catch (Exception ex) {
            log.error("list online users failed", ex);
            throw ex;
        }
    }

    private String prefix() {
        String v = environment.getProperty("geelato.online-user.redis-prefix");
        return StringUtils.isNotEmpty(v) ? v : DEFAULT_PREFIX;
    }

    private String indexKey() {
        return prefix() + ":user_index";
    }

    private String userKey(String tenantCode, String userId) {
        return prefix() + ":user:" + tenantCode + ":" + userId;
    }

    private String member(String tenantCode, String userId) {
        return tenantCode + ":" + userId;
    }

    private ParsedMember parseMember(String member) {
        int idx = member.indexOf(':');
        if (idx <= 0 || idx == member.length() - 1) {
            return null;
        }
        String tenantCode = member.substring(0, idx);
        String userId = member.substring(idx + 1);
        if (StringUtils.isEmpty(tenantCode) || StringUtils.isEmpty(userId)) {
            return null;
        }
        return new ParsedMember(tenantCode, userId);
    }

    private int getWindowMinutes(Integer override) {
        if (override != null && override > 0) {
            return override;
        }
        Integer v = environment.getProperty("geelato.online-user.window-minutes", Integer.class);
        return (v != null && v > 0) ? v : DEFAULT_WINDOW_MINUTES;
    }

    private int getMaxReturn(Integer override) {
        if (override != null && override > 0) {
            return override;
        }
        Integer v = environment.getProperty("geelato.online-user.max-return", Integer.class);
        return (v != null && v > 0) ? v : DEFAULT_MAX_RETURN;
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private String stringValue(Object v, String defaultValue) {
        if (v == null) {
            return defaultValue;
        }
        String s = String.valueOf(v);
        return StringUtils.isNotEmpty(s) ? s : defaultValue;
    }

    private Long longValue(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class ParsedMember {
        final String tenantCode;
        final String userId;

        ParsedMember(String tenantCode, String userId) {
            this.tenantCode = tenantCode;
            this.userId = userId;
        }
    }
}
