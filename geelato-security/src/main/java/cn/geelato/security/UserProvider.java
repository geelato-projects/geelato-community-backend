package cn.geelato.security;

import lombok.Getter;
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

    public abstract void loadData(Object userData);
}
