package cn.geelato.security;

import lombok.Getter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public abstract class UserProvider {
    protected Map<String, User> userDataMap = new ConcurrentHashMap<>();

    public String getUserName(String userId){
        User user = userDataMap.get(userId);
        return user == null ? "" : user.getUserName();
    }

    public User getUser(String userId){
        return userDataMap.get(userId);
    }
    
    public User getUserById(String userId){
        return getUser(userId);
    }

    public User getUserByExtendKey(String extendKey, String type) {
        if (extendKey == null || extendKey.isEmpty()) {
            return null;
        }
        String normalized = normalizeType(type);
        for (User user : userDataMap.values()) {
            if ("loginName".equals(normalized) && extendKey.equals(user.getLoginName())) {
                return user;
            }
            if ("weixinUnionId".equals(normalized) && extendKey.equals(user.getWeixinUnionId())) {
                return user;
            }
            if ("weixinWorkUserId".equals(normalized) && extendKey.equals(user.getWeixinWorkUserId())) {
                return user;
            }
        }
        return null;
    }

    public List<UserRole> getUserRoles(String userId){
        User user = userDataMap.get(userId);
        if(user == null || user.getUserRoles() == null){
            return Collections.emptyList();
        }
        return user.getUserRoles();
    }

    public List<UserOrg> getUserOrgs(String userId){
        User user = userDataMap.get(userId);
        if(user == null || user.getUserOrgs() == null){
            return Collections.emptyList();
        }
        return user.getUserOrgs();
    }

    protected void putUser(User user) {
        if (user == null || user.getUserId() == null || user.getUserId().isEmpty()) {
            return;
        }
        userDataMap.put(user.getUserId(), user);
    }

    protected void putUsers(Collection<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (User user : users) {
            putUser(user);
        }
    }

    protected String normalizeType(String type) {
        if (type == null || type.isEmpty()) {
            return "";
        }
        if ("loginName".equalsIgnoreCase(type) || "login_name".equalsIgnoreCase(type)) {
            return "loginName";
        }
        if ("weixinUnionId".equalsIgnoreCase(type) || "weixin_unionId".equalsIgnoreCase(type) || "unionId".equalsIgnoreCase(type)) {
            return "weixinUnionId";
        }
        if ("weixinWorkUserId".equalsIgnoreCase(type) || "weixin_work_userId".equalsIgnoreCase(type) || "workUserId".equalsIgnoreCase(type)) {
            return "weixinWorkUserId";
        }
        return type;
    }

    public abstract void loadData(Object userData);
}
