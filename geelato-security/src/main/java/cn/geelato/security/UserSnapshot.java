package cn.geelato.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserSnapshot {
    private static final UserSnapshot EMPTY = new UserSnapshot(Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, User> userById;
    private final Map<String, Map<String, User>> userByExtendType;

    private UserSnapshot(Map<String, User> userById, Map<String, Map<String, User>> userByExtendType) {
        this.userById = userById;
        this.userByExtendType = userByExtendType;
    }

    static UserSnapshot empty() {
        return EMPTY;
    }

    static UserSnapshot from(Map<String, User> userById, Map<String, Map<String, User>> userByExtendType) {
        Map<String, Map<String, User>> extendIndex = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, User>> entry : userByExtendType.entrySet()) {
            extendIndex.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return new UserSnapshot(
                Collections.unmodifiableMap(new LinkedHashMap<>(userById)),
                Collections.unmodifiableMap(extendIndex)
        );
    }

    User getUser(String userId) {
        return userById.get(userId);
    }

    User getUserByExtendKey(String type, String extendKey) {
        Map<String, User> pool = userByExtendType.get(type);
        if (pool == null) {
            return null;
        }
        return pool.get(extendKey);
    }
}
