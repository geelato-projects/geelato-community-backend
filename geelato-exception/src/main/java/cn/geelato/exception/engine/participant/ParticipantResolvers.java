package cn.geelato.exception.engine.participant;

import cn.geelato.security.SecurityProvider;
import cn.geelato.security.User;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 六类选择器求解实现（统一经 SecurityProvider 契约，实现方未接入时按可用性降级）：
 * USER 显式用户 / ROLE 角色全员 / DEPT 部门全员 / DEPT_ROLE 部门角色（org.scope=BIZ_ORG 时取命中对象业务组织）/
 * BIZ_FIELD 命中对象字段（ownerId）/ ORG_TREE_UP 沿组织树向上 level 层按部门角色求解（升级链）。
 */
@Slf4j
public final class ParticipantResolvers {

    private ParticipantResolvers() {
    }

    public static List<ParticipantResolver> defaults(SecurityProvider provider) {
        List<ParticipantResolver> resolvers = new ArrayList<>();
        resolvers.add(new UserResolver(provider));
        resolvers.add(new RoleResolver(provider));
        resolvers.add(new DeptResolver(provider));
        resolvers.add(new DeptRoleResolver(provider));
        resolvers.add(new BizFieldResolver());
        resolvers.add(new OrgTreeUpResolver(provider));
        return resolvers;
    }

    static String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    static Participant of(User user) {
        return user == null ? null : new Participant(user.getUserId(), user.getUserName(), user.getOrgId());
    }

    public static class UserResolver implements ParticipantResolver {
        private final SecurityProvider provider;

        UserResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "USER";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit) {
            String userId = str(selector, "userId");
            List<Participant> out = new ArrayList<>();
            if (userId == null || userId.isBlank()) {
                return out;
            }
            if (provider != null) {
                User user = provider.getUserById(userId);
                if (user != null) {
                    out.add(of(user));
                    return out;
                }
            }
            out.add(new Participant(userId, userId, null));
            return out;
        }
    }

    public static class RoleResolver implements ParticipantResolver {
        private final SecurityProvider provider;

        RoleResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "ROLE";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit) {
            String roleCode = str(selector, "roleCode");
            if (provider == null || roleCode == null || roleCode.isBlank()) {
                return new ArrayList<>();
            }
            List<Participant> out = new ArrayList<>();
            for (User user : provider.queryUsersOfRole(roleCode)) {
                Participant p = of(user);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }
    }

    public static class DeptResolver implements ParticipantResolver {
        private final SecurityProvider provider;

        DeptResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "DEPT";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit) {
            String orgId = resolveOrgId(selector, hit);
            if (provider == null || orgId == null || orgId.isBlank()) {
                return new ArrayList<>();
            }
            List<Participant> out = new ArrayList<>();
            for (User user : provider.queryUsersOfOrg(orgId)) {
                Participant p = of(user);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }
    }

    public static class DeptRoleResolver implements ParticipantResolver {
        private final SecurityProvider provider;

        DeptRoleResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "DEPT_ROLE";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit) {
            String orgId = resolveOrgId(selector, hit);
            String roleCode = str(selector, "roleCode");
            if (provider == null || orgId == null || orgId.isBlank() || roleCode == null || roleCode.isBlank()) {
                return new ArrayList<>();
            }
            List<Participant> out = new ArrayList<>();
            for (User user : provider.queryUsersOfOrgRole(orgId, roleCode)) {
                Participant p = of(user);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }
    }

    public static class BizFieldResolver implements ParticipantResolver {
        @Override
        public String type() {
            return "BIZ_FIELD";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit) {
            String ownerId = str(hit, "ownerId");
            List<Participant> out = new ArrayList<>();
            if (ownerId != null && !ownerId.isBlank()) {
                out.add(new Participant(ownerId, str(hit, "ownerName"), str(hit, "ownerOrgId")));
            }
            return out;
        }
    }

    /**
     * 沿组织树向上按层级取部门角色人（升级链 E1/E2）：命中对象业务组织为基点，
     * queryAncestorOrgs（直接上级到根）取第 level 层（越界取根），再按该组织 + roleCode 求解。
     */
    public static class OrgTreeUpResolver implements ParticipantResolver {
        private final SecurityProvider provider;

        OrgTreeUpResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "ORG_TREE_UP";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Map<String, Object> hit) {
            String roleCode = str(selector, "roleCode");
            String orgId = resolveOrgId(selector, hit);
            if (provider == null || orgId == null || orgId.isBlank() || roleCode == null || roleCode.isBlank()) {
                return new ArrayList<>();
            }
            int level = asInt(selector.get("level"), 1);
            List<cn.geelato.security.Org> ancestors = provider.queryAncestorOrgs(orgId);
            if (ancestors == null || ancestors.isEmpty()) {
                return new ArrayList<>();
            }
            int index = Math.min(Math.max(1, level), ancestors.size()) - 1;
            cn.geelato.security.Org target = ancestors.get(index);
            if (target == null || target.getOrgId() == null) {
                return new ArrayList<>();
            }
            List<Participant> out = new ArrayList<>();
            for (User user : provider.queryUsersOfOrgRole(target.getOrgId(), roleCode)) {
                Participant p = of(user);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }

        private static int asInt(Object value, int fallback) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    static String resolveOrgId(Map<String, Object> selector, Map<String, Object> hit) {
        String orgId = str(selector, "orgId");
        if (orgId != null && !orgId.isBlank()) {
            return orgId;
        }
        Object org = selector == null ? null : selector.get("org");
        String scope = org instanceof Map<?, ?> m ? String.valueOf(m.get("scope")) : null;
        if ("BIZ_ORG".equals(scope)) {
            return str(hit, "ownerOrgId");
        }
        return null;
    }
}
