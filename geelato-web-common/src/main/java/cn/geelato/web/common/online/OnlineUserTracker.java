package cn.geelato.web.common.online;

import cn.geelato.security.User;
import jakarta.servlet.http.HttpServletRequest;

public interface OnlineUserTracker {
    void touch(User user, HttpServletRequest request);
}

