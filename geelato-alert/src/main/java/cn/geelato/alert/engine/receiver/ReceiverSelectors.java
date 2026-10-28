package cn.geelato.alert.engine.receiver;

import cn.geelato.security.SecurityProvider;
import cn.geelato.security.User;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 六类接收人选择器实现（统一经 SecurityProvider 契约，与异常中心 ParticipantResolvers 同套语义）：
 * USER 显式用户 / ROLE 角色全员 / DEPT 部门全员 / DEPT_ROLE 部门角色（org.scope=BIZ_ORG 时取命中源实例业务组织）/
 * BIZ_FIELD 命中源实例处理人 / ORG_TREE_UP 沿组织树向上 level 层按部门角色求解（升级链）。
 */
@Slf4j
public final class ReceiverSelectors {

    private ReceiverSelectors() {
    }

    public static List<ReceiverSelectorResolver> defaults(SecurityProvider provider) {
        List<ReceiverSelectorResolver> resolvers = new ArrayList<>();
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

    /** 选择器取值：单数键与复数键均支持，合并返回。 */
    static List<String> selectorValues(Map<String, Object> selector, String singleKey, String pluralKey) {
        List<String> values = new ArrayList<>();
        Object single = selector == null ? null : selector.get(singleKey);
        if (single != null && !String.valueOf(single).isBlank()) {
            values.add(String.valueOf(single));
        }
        Object plural = selector == null ? null : selector.get(pluralKey);
        if (plural instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    values.add(String.valueOf(item));
                }
            }
        }
        return values;
    }

    static AlertReceiver of(User user) {
        return user == null ? null : new AlertReceiver(user.getUserId(), user.getUserName(), user.getOrgId());
    }

    public static class UserResolver implements ReceiverSelectorResolver {
        private final SecurityProvider provider;

        UserResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "USER";
        }

        @Override
        public List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx) {
            List<AlertReceiver> out = new ArrayList<>();
            for (String userId : selectorValues(selector, "userId", "userIds")) {
                if (provider != null) {
                    User user = provider.getUserById(userId);
                    if (user != null) {
                        out.add(of(user));
                        continue;
                    }
                }
                out.add(new AlertReceiver(userId, userId, null));
            }
            return out;
        }
    }

    public static class RoleResolver implements ReceiverSelectorResolver {
        private final SecurityProvider provider;

        RoleResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "ROLE";
        }

        @Override
        public List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx) {
            List<String> roleCodes = selectorValues(selector, "roleCode", "roleCodes");
            if (provider == null || roleCodes.isEmpty()) {
                return new ArrayList<>();
            }
            List<AlertReceiver> out = new ArrayList<>();
            for (String roleCode : roleCodes) {
                for (User user : provider.queryUsersOfRole(roleCode)) {
                    AlertReceiver r = of(user);
                    if (r != null) {
                        out.add(r);
                    }
                }
            }
            return out;
        }
    }

    public static class DeptResolver implements ReceiverSelectorResolver {
        private final SecurityProvider provider;

        DeptResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "DEPT";
        }

        @Override
        public List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx) {
            String orgId = resolveOrgId(selector, ctx);
            if (provider == null || orgId == null || orgId.isBlank()) {
                return new ArrayList<>();
            }
            List<AlertReceiver> out = new ArrayList<>();
            for (User user : provider.queryUsersOfOrg(orgId)) {
                AlertReceiver r = of(user);
                if (r != null) {
                    out.add(r);
                }
            }
            return out;
        }
    }

    public static class DeptRoleResolver implements ReceiverSelectorResolver {
        private final SecurityProvider provider;

        DeptRoleResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "DEPT_ROLE";
        }

        @Override
        public List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx) {
            String orgId = resolveOrgId(selector, ctx);
            List<String> roleCodes = selectorValues(selector, "roleCode", "roleCodes");
            if (provider == null || orgId == null || orgId.isBlank() || roleCodes.isEmpty()) {
                return new ArrayList<>();
            }
            List<AlertReceiver> out = new ArrayList<>();
            for (String roleCode : roleCodes) {
                for (User user : provider.queryUsersOfOrgRole(orgId, roleCode)) {
                    AlertReceiver r = of(user);
                    if (r != null) {
                        out.add(r);
                    }
                }
            }
            return out;
        }
    }

    public static class BizFieldResolver implements ReceiverSelectorResolver {
        @Override
        public String type() {
            return "BIZ_FIELD";
        }

        @Override
        public List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx) {
            List<AlertReceiver> out = new ArrayList<>();
            if (ctx != null && ctx.getOwnerId() != null && !ctx.getOwnerId().isBlank()) {
                out.add(new AlertReceiver(ctx.getOwnerId(), ctx.getOwnerName(), ctx.getOwnerOrgId()));
            }
            return out;
        }
    }

    /** 沿组织树向上按层级取部门角色人（升级链）：命中源实例业务组织为基点，取第 level 层（越界取根），再按该组织 + roleCode 求解。 */
    public static class OrgTreeUpResolver implements ReceiverSelectorResolver {
        private final SecurityProvider provider;

        OrgTreeUpResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "ORG_TREE_UP";
        }

        @Override
        public List<AlertReceiver> resolve(Map<String, Object> selector, AlertResolveCtx ctx) {
            String roleCode = str(selector, "roleCode");
            String orgId = resolveOrgId(selector, ctx);
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
            List<AlertReceiver> out = new ArrayList<>();
            for (User user : provider.queryUsersOfOrgRole(target.getOrgId(), roleCode)) {
                AlertReceiver r = of(user);
                if (r != null) {
                    out.add(r);
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

    static String resolveOrgId(Map<String, Object> selector, AlertResolveCtx ctx) {
        String orgId = str(selector, "orgId");
        if (orgId != null && !orgId.isBlank()) {
            return orgId;
        }
        Object org = selector == null ? null : selector.get("org");
        String scope = org instanceof Map<?, ?> m ? String.valueOf(m.get("scope")) : null;
        if ("BIZ_ORG".equals(scope)) {
            return ctx == null ? null : ctx.getOwnerOrgId();
        }
        return null;
    }
}
