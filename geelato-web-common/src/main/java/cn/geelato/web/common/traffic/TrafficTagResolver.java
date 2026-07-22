package cn.geelato.web.common.traffic;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.User;
import cn.geelato.traffic.TrafficTagStrategy;
import cn.geelato.utils.StringUtils;
import cn.geelato.logging.LogContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.regex.Pattern;

@Slf4j
public class TrafficTagResolver {
    private static final String VERSION = "v1";
    private static final Pattern TAG_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final TrafficColoringProperties properties;
    private final TrafficTagSigner signer;
    private final TrafficTagStrategy strategy;

    public TrafficTagResolver(TrafficColoringProperties properties) {
        this(properties, null);
    }

    public TrafficTagResolver(TrafficColoringProperties properties, TrafficTagStrategy strategy) {
        this.properties = properties;
        this.signer = new TrafficTagSigner(properties == null ? null : properties.getSigningSecret());
        this.strategy = strategy;
    }

    public String resolveAndApply(HttpServletRequest request, HttpServletResponse response) {
        if (properties == null || !properties.isEnabled() || request == null || response == null) {
            return null;
        }
        String resolved = null;
        boolean needSetCookie = false;
        try {
            String override = resolveOverride(request);
            if (StringUtils.isNotEmpty(override)) {
                resolved = override;
                needSetCookie = true;
            } else {
                ParsedCookie parsed = parseCookie(request);
                if (parsed != null) {
                    resolved = parsed.tag;
                } else {
                    resolved = computeTagByStrategy(request, null);
                    needSetCookie = true;
                }
            }
        } catch (Exception ex) {
            log.debug("resolve traffic tag failed", ex);
            resolved = defaultTag();
            needSetCookie = true;
        }

        TrafficTagContext.set(resolved);
        if (StringUtils.isNotEmpty(properties.getRequestAttributeKey())) {
            request.setAttribute(properties.getRequestAttributeKey(), resolved);
        }
        LogContext.put(properties.getMdcKey(), resolved);
        if (StringUtils.isNotEmpty(properties.getTagHeaderName())) {
            response.setHeader(properties.getTagHeaderName(), resolved);
        }
        if (needSetCookie) {
            writeCookie(response, resolved);
        }
        return resolved;
    }

    public void applyAfterAuthenticated(User user, HttpServletRequest request, HttpServletResponse response) {
        if (properties == null || !properties.isEnabled() || request == null || response == null || user == null) {
            return;
        }
        String override = resolveOverride(request);
        if (StringUtils.isNotEmpty(override)) {
            return;
        }
        String expected = computeTagByStrategy(request, user);
        String current = TrafficTagContext.get();
        if (StringUtils.isNotEmpty(current) && current.equals(expected)) {
            return;
        }
        TrafficTagContext.set(expected);
        if (StringUtils.isNotEmpty(properties.getRequestAttributeKey())) {
            request.setAttribute(properties.getRequestAttributeKey(), expected);
        }
        LogContext.put(properties.getMdcKey(), expected);
        if (StringUtils.isNotEmpty(properties.getTagHeaderName())) {
            response.setHeader(properties.getTagHeaderName(), expected);
        }
        writeCookie(response, expected);
    }

    private String resolveOverride(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if ("product".equalsIgnoreCase(GlobalContext.getEnvironment())) {
            return null;
        }
        String raw = null;
        if (StringUtils.isNotEmpty(properties.getOverrideHeaderName())) {
            raw = request.getHeader(properties.getOverrideHeaderName());
        }
        if (StringUtils.isEmpty(raw) && StringUtils.isNotEmpty(properties.getOverrideQueryName())) {
            raw = request.getParameter(properties.getOverrideQueryName());
        }
        raw = StringUtils.isNotEmpty(raw) ? raw.trim() : null;
        return isValidTag(raw) ? raw : null;
    }

    private ParsedCookie parseCookie(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String name = properties.getTagCookieName();
        if (StringUtils.isEmpty(name)) {
            return null;
        }
        for (Cookie c : cookies) {
            if (c != null && name.equals(c.getName())) {
                return parseCookieValue(c.getValue());
            }
        }
        return null;
    }

    private String computeTagByStrategy(HttpServletRequest request, User user) {
        try {
            if (strategy != null) {
                String v = strategy.resolveTag(request, user);
                if (isValidTag(v)) {
                    return v;
                }
            }
        } catch (Exception e) {
            log.debug("resolve traffic tag by strategy failed", e);
        }
        return defaultTag();
    }

    private ParsedCookie parseCookieValue(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        if (!properties.isSigningEnabled()) {
            String tag = value.trim();
            return isValidTag(tag) ? new ParsedCookie(tag, null) : null;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        if (!VERSION.equals(parts[0])) {
            return null;
        }
        String tag = parts[1];
        String iat = parts[2];
        String sig = parts[3];
        if (!isValidTag(tag) || StringUtils.isEmpty(iat) || StringUtils.isEmpty(sig)) {
            return null;
        }
        try {
            Long.parseLong(iat);
        } catch (Exception e) {
            return null;
        }
        String payload = payload(tag, iat);
        return signer.verify(payload, sig) ? new ParsedCookie(tag, iat) : null;
    }

    private void writeCookie(HttpServletResponse response, String tag) {
        if (response == null || StringUtils.isEmpty(properties.getTagCookieName()) || !isValidTag(tag)) {
            return;
        }
        String value;
        if (properties.isSigningEnabled()) {
            String iat = String.valueOf(Instant.now().getEpochSecond());
            String payload = payload(tag, iat);
            String sig = signer.sign(payload);
            value = VERSION + "." + tag + "." + iat + "." + sig;
        } else {
            value = tag;
        }
        Cookie cookie = new Cookie(properties.getTagCookieName(), value);
        cookie.setPath(StringUtils.isNotEmpty(properties.getCookiePath()) ? properties.getCookiePath() : "/");
        cookie.setMaxAge(Math.max(properties.getCookieMaxAgeSeconds(), 0));
        cookie.setHttpOnly(properties.isCookieHttpOnly());
        cookie.setSecure(properties.isCookieSecure());
        if (StringUtils.isNotEmpty(properties.getCookieDomain())) {
            cookie.setDomain(properties.getCookieDomain());
        }
        response.addCookie(cookie);
    }

    private String payload(String tag, String iat) {
        return VERSION + "." + tag + "." + iat;
    }

    private String defaultTag() {
        String v = properties.getDefaultTag();
        v = StringUtils.isNotEmpty(v) ? v.trim() : null;
        return isValidTag(v) ? v : "default";
    }

    private String grayTag() {
        String v = properties.getGrayTag();
        v = StringUtils.isNotEmpty(v) ? v.trim() : null;
        return isValidTag(v) ? v : "gray";
    }

    private boolean isValidTag(String tag) {
        return StringUtils.isNotEmpty(tag) && TAG_PATTERN.matcher(tag).matches();
    }

    private static class ParsedCookie {
        final String tag;
        final String iat;

        ParsedCookie(String tag, String iat) {
            this.tag = tag;
            this.iat = iat;
        }
    }
}
