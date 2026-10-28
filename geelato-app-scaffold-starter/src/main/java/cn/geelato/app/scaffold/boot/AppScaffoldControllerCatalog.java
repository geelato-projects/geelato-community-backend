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
                "cn.geelato.web.platform.srv.meta.MetaController"
        )));

        map.put(AppScaffoldCapability.DICTIONARY, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.base.DictionaryController",
                "cn.geelato.web.platform.srv.platform.DictController",
                "cn.geelato.web.platform.srv.platform.DictItemController"
        )));

        map.put(AppScaffoldCapability.NOTICE, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.notification.NotificationController"
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

        // 应用（含多语言/表/视图/SQL 授权）：原由下游 extra-controllers 逐条声明，现收敛为脚手架默认能力
        map.put(AppScaffoldCapability.APP, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.platform.AppController",
                "cn.geelato.web.platform.srv.platform.AppMultiLangController",
                "cn.geelato.web.platform.srv.platform.AppTableMapController",
                "cn.geelato.web.platform.srv.platform.AppViewMapController",
                "cn.geelato.web.platform.srv.platform.AppSqlMapController"
        )));

        // 调度管理：/api/schedule* 与调度日志
        map.put(AppScaffoldCapability.SCHEDULE, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.platform.ScheduleController",
                "cn.geelato.web.platform.srv.platform.ScheduleLogController"
        )));

        // SQL 配置：/api/sql* 动态 SQL 定义
        map.put(AppScaffoldCapability.SQL, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.platform.SqlController"
        )));

        // 脚本接口：接口定义/参数/执行/外部调用/IDE 函数提示
        map.put(AppScaffoldCapability.SCRIPT, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.script.ApiController",
                "cn.geelato.web.platform.srv.script.ApiParamController",
                "cn.geelato.web.platform.srv.script.ServiceController",
                "cn.geelato.web.platform.srv.script.ExtServiceController",
                "cn.geelato.web.platform.srv.script.GraalServiceController"
        )));

        // 静态站点：站点 CRUD + 站点树/文件管理
        map.put(AppScaffoldCapability.SITE, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.site.StaticSiteController"
        )));

        // 运行期导出：/api/export/file/*
        map.put(AppScaffoldCapability.EXPORT, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.excel.ExportExcelController"
        )));

        // 旧建模口接回：仅 DevTableColumnController（createOrUpdate 内带 refreshDBMeta 回投）。
        // 禁加同族 DevDbConnectController：其与下游自实现 ModelConnectController 同为
        // jdbcConnect 映射 ⇒ Ambiguous mapping 令上下文整栈起不来。
        map.put(AppScaffoldCapability.MODEL, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.model.DevTableColumnController"
        )));

        // 设计态 DDL 端点：/api/meta/ddl/*（含 /redis/refresh 等数据源元数据缓存刷新）
        map.put(AppScaffoldCapability.DDL, new LinkedHashSet<>(Set.of(
                "cn.geelato.web.platform.srv.meta.MetaDdlController"
        )));

        CONTROLLERS = Collections.unmodifiableMap(map);
    }

    public Set<String> controllersBy(AppScaffoldCapability capability) {
        return CONTROLLERS.getOrDefault(capability, Set.of());
    }
}
