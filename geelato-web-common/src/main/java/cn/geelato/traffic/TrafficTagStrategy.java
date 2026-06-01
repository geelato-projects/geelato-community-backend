package cn.geelato.traffic;

import cn.geelato.security.User;
import jakarta.servlet.http.HttpServletRequest;

public interface TrafficTagStrategy {
    String resolveTag(HttpServletRequest request, User user);
}

