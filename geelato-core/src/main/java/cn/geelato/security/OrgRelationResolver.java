package cn.geelato.security;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class OrgRelationResolver {
    private final Map<String, Org> orgById;

    OrgRelationResolver(Map<String, Org> orgById) {
        this.orgById = orgById;
    }

    String resolveDeptId(String orgId) {
        Org org = orgById.get(orgId);
        if (org == null) {
            return "";
        }
        if (isType(org, "department")) {
            return org.getOrgId();
        }
        String currentId = org.getOrgId();
        String pid = org.getPid();
        Set<String> visited = new HashSet<>();
        while (pid != null && !pid.isEmpty() && visited.add(pid)) {
            Org parent = orgById.get(pid);
            if (parent == null) {
                break;
            }
            if (isType(parent, "department")) {
                return parent.getOrgId();
            }
            currentId = parent.getOrgId();
            pid = parent.getPid();
        }
        return currentId == null ? "" : currentId;
    }

    String resolveCompanyId(String orgId) {
        Org org = orgById.get(orgId);
        if (org == null) {
            return "";
        }
        if (isType(org, "company")) {
            return org.getOrgId();
        }
        String currentId = org.getOrgId();
        String pid = org.getPid();
        Set<String> visited = new HashSet<>();
        while (pid != null && !pid.isEmpty() && visited.add(pid)) {
            Org parent = orgById.get(pid);
            if (parent == null) {
                break;
            }
            if (isType(parent, "company")) {
                return parent.getOrgId();
            }
            currentId = parent.getOrgId();
            pid = parent.getPid();
        }
        return currentId == null ? "" : currentId;
    }

    private boolean isType(Org org, String expectedType) {
        return org.getType() != null && org.getType().equalsIgnoreCase(expectedType);
    }
}
