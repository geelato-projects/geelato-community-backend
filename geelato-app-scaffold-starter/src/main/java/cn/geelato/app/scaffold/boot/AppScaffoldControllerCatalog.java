package cn.geelato.app.scaffold.boot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AppScaffoldControllerCatalog {
    private static final Map<AppScaffoldCapability, Set<String>> CONTROLLERS;

    static {
        Map<AppScaffoldCapability, Set<String>> map = new EnumMap<>(AppScaffoldCapability.class);

        map.put(AppScaffoldCapability.LOGIN, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.auth.JWTAuthController",
                "cn.geelato.web.platform.srv.auth.OAuth2Controller"
        )));

        map.put(AppScaffoldCapability.MQL, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.meta.MetaRuntimeController"
        )));

        map.put(AppScaffoldCapability.DICTIONARY, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.base.DictionaryController",
                "cn.geelato.web.platform.srv.platform.DictController",
                "cn.geelato.web.platform.srv.platform.DictItemController"
        )));

        map.put(AppScaffoldCapability.ORGANIZATION, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.security.OrgController",
                "cn.geelato.web.platform.srv.security.OrgUserMapController"
        )));

        map.put(AppScaffoldCapability.USER, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.security.UserController",
                "cn.geelato.web.platform.srv.security.RoleController",
                "cn.geelato.web.platform.srv.security.PermissionController",
                "cn.geelato.web.platform.srv.security.RoleUserMapController",
                "cn.geelato.web.platform.srv.security.RolePermissionMapController"
        )));

        map.put(AppScaffoldCapability.UPLOAD, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.base.UploadController",
                "cn.geelato.web.platform.srv.base.DownloadController",
                "cn.geelato.web.platform.srv.file.AttachController",
                "cn.geelato.web.platform.srv.file.OssController"
        )));

        CONTROLLERS = Collections.unmodifiableMap(map);
    }

    public Set<String> controllersBy(AppScaffoldCapability capability) {
        return CONTROLLERS.getOrDefault(capability, Set.of());
    }
}
