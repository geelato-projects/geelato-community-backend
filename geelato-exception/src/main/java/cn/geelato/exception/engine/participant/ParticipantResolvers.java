package cn.geelato.exception.engine.participant;

import cn.geelato.exception.engine.core.Hit;
import cn.geelato.security.SecurityProvider;
import cn.geelato.security.User;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 六类选择器求解实现（统一经 SecurityProvider 契约，实现方未接入时按可用性降级）：
 * USER 显式用户 / ROLE 角色全员 / DEPT 部门全员 / DEPT_ROLE 部门角色（org.scope=BIZ_ORG 时取命中对象业务组织）/
 * BIZ_FIELD 命中对象业务字段（field/fields 指定，缺省 ownerId）/ ORG_TREE_UP 沿组织树向上 level 层按部门角色求解（升级链）。
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
        resolvers.add(new BizFieldResolver(provider));
        resolvers.add(new OrgTreeUpResolver(provider));
        return resolvers;
    }

    static String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 通配 Map 取值（对象形态字段 {userId|id,...} / org 选择器读取）。 */
    static String val(Map<?, ?> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 选择器取值：单数键（userId/roleCode）与复数键（userIds/roleCodes）均支持，合并返回。 */
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
        public List<Participant> resolve(Map<String, Object> selector, Hit hit) {
            List<Participant> out = new ArrayList<>();
            for (String userId : selectorValues(selector, "userId", "userIds")) {
                if (provider != null) {
                    User user = provider.getUserById(userId);
                    if (user != null) {
                        out.add(of(user));
                        continue;
                    }
                }
                out.add(new Participant(userId, userId, null));
            }
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
        public List<Participant> resolve(Map<String, Object> selector, Hit hit) {
            List<String> roleCodes = selectorValues(selector, "roleCode", "roleCodes");
            if (provider == null || roleCodes.isEmpty()) {
                return new ArrayList<>();
            }
            List<Participant> out = new ArrayList<>();
            for (String roleCode : roleCodes) {
                for (User user : provider.queryUsersOfRole(roleCode)) {
                    Participant p = of(user);
                    if (p != null) {
                        out.add(p);
                    }
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
        public List<Participant> resolve(Map<String, Object> selector, Hit hit) {
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
        public List<Participant> resolve(Map<String, Object> selector, Hit hit) {
            String orgId = resolveOrgId(selector, hit);
            List<String> roleCodes = selectorValues(selector, "roleCode", "roleCodes");
            if (provider == null || orgId == null || orgId.isBlank() || roleCodes.isEmpty()) {
                return new ArrayList<>();
            }
            List<Participant> out = new ArrayList<>();
            for (String roleCode : roleCodes) {
                for (User user : provider.queryUsersOfOrgRole(orgId, roleCode)) {
                    Participant p = of(user);
                    if (p != null) {
                        out.add(p);
                    }
                }
            }
            return out;
        }
    }

    /**
     * 命中对象业务字段取人：field/fields 声明字段名（契约字段或管道脚本返回的 extras 扩展字段，
     * 如订单的 operatorId/salesmanIds），值支持单值、数组（多人）与 {userId|id, userName?|name?} 对象；
     * fields 必须显式声明（无缺省锚点），未声明即求解为空（上游留痕跳过）。
     * id 经 SecurityProvider 回填姓名/组织，查不到原样透传。
     */
    public static class BizFieldResolver implements ParticipantResolver {
        private final SecurityProvider provider;

        BizFieldResolver(SecurityProvider provider) {
            this.provider = provider;
        }

        @Override
        public String type() {
            return "BIZ_FIELD";
        }

        @Override
        public List<Participant> resolve(Map<String, Object> selector, Hit hit) {
            List<String> fields = selectorValues(selector, "field", "fields");
            List<Participant> out = new ArrayList<>();
            for (String field : fields) {
                for (Object value : userValuesOf(hit.getField(field))) {
                    Participant p = participantOf(value);
                    if (p != null) {
                        out.add(p);
                    }
                }
            }
            return out;
        }

        /** 字段值 → 用户值列表：单值原样、数组展开、null/空白跳过。 */
        private static List<Object> userValuesOf(Object value) {
            List<Object> out = new ArrayList<>();
            if (value instanceof List<?> list) {
                out.addAll(list);
            } else if (value != null) {
                out.add(value);
            }
            return out;
        }

        /** 用户值 → 参与者：{userId|id, userName?|name?} 对象取键，其余按字符串 id；
         *  SecurityProvider 可查则回填姓名/组织，查不到原样透传（与 USER 口径一致）。 */
        private Participant participantOf(Object value) {
            String userId = null;
            String userName = null;
            if (value instanceof Map<?, ?> m) {
                userId = firstNonBlank(val(m, "userId"), val(m, "id"));
                userName = firstNonBlank(val(m, "userName"), val(m, "name"));
            } else if (value != null && !String.valueOf(value).isBlank()) {
                userId = String.valueOf(value);
            }
            if (userId == null || userId.isBlank()) {
                return null;
            }
            if (provider != null) {
                User user = provider.getUserById(userId);
                if (user != null) {
                    return of(user);
                }
            }
            return new Participant(userId, userName == null || userName.isBlank() ? userId : userName, null);
        }

        private static String firstNonBlank(String a, String b) {
            return a != null && !a.isBlank() ? a : b;
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
        public List<Participant> resolve(Map<String, Object> selector, Hit hit) {
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

    static String resolveOrgId(Map<String, Object> selector, Hit hit) {
        String orgId = str(selector, "orgId");
        if (orgId != null && !orgId.isBlank()) {
            return orgId;
        }
        Object org = selector == null ? null : selector.get("org");
        String scope = org instanceof Map<?, ?> m ? String.valueOf(m.get("scope")) : null;
        if ("BIZ_FIELD".equals(scope)) {
            String field = org instanceof Map<?, ?> m ? val(m, "field") : null;
            Object value = field == null ? null : hit.getField(field);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
