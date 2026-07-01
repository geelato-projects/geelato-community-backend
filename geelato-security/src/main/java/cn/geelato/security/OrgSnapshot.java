package cn.geelato.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OrgSnapshot {
    private static final OrgSnapshot EMPTY = new OrgSnapshot(Collections.emptyMap());

    private final Map<String, Org> orgById;

    private OrgSnapshot(Map<String, Org> orgById) {
        this.orgById = orgById;
    }

    static OrgSnapshot empty() {
        return EMPTY;
    }

    static OrgSnapshot from(Map<String, Org> orgById) {
        return new OrgSnapshot(Collections.unmodifiableMap(new LinkedHashMap<>(orgById)));
    }

    Org getOrg(String orgId) {
        return orgById.get(orgId);
    }

    Map<String, Org> getOrgById() {
        return orgById;
    }
}
