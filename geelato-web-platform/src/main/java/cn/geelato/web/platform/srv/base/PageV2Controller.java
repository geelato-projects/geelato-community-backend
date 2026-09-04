package cn.geelato.web.platform.srv.base;

import cn.geelato.core.SessionCtx;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.security.App;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.utils.CacheUtil;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.AppPage;
import cn.geelato.meta.AppPageLang;
import cn.geelato.meta.AppPageLog;
import cn.geelato.web.platform.event.UpgradePageEvent;
import cn.geelato.web.common.event.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 页面配置接口 V2（性能优化版，路径与 V1 完全一致，前端零改动）：
 * <p>
 * 1. getPageAndCustom 运行时路径的 pageLang/pageCustom/pagePerms 三段并行查询
 *    （V1 为 page→pageLang→pageCustom→pagePerms 四次串行 DB 往返，冷缓存时是接口耗时的大头）；
 * 2. 运行时路径 releaseContent 以原生 JSON 对象返回，消除 JSON 字符串内嵌 JSON 的双重转义膨胀
 *    与前端二次解析（前端 GlPageViewer.parseReleaseContent 已做字符串/对象双形态兼容）；
 * 3. 并行任务通过 withRequestContext 在池线程重建请求线程的安全上下文
 *    （SecurityContext 用户/租户/应用 + primary 数据源），任务结束清理。
 * <p>
 * 旧实现保留在 {@link PageController}（/page_v1）作为回退参照：
 * 回退时把本类类级标记改回 /page_v1、PageController 改回 /page 即可，无需改配置。
 *
 * @author itechgee@126.com
 */
@ApiRestController("/page")
@Slf4j
public class PageV2Controller extends BaseController {

    /** 页面查询并行执行线程池（CallerRuns 语义，池满退化为调用线程执行，见 GlPageExecutorConfiguration） */
    @Autowired
    @Qualifier("glPageExecutor")
    private Executor glPageExecutor;

    /** 全局 ObjectMapper（见 SerializerConfiguration），用于把 releaseContent 解析为原生 JSON 对象 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 基于页面id或页面的扩展id（树节点id）获取页面定义及页面自定义信息
     *
     * 用于运行时，页面渲染时使用。返回的页面中没有页面源码字段
     *
     * @param idType “pageId”或“extendId”
     * @param id     id值
     * @return {id,type,appId,code,releaseContent,pageCustom,pagePermission}，其中pageCustom为不同用户对该页面的自定义信息
     */
    @RequestMapping(value = {"/getPageAndCustom/{idType}/{id}", "getPageAndCustom/{idType}/{id}/*"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageAndCustom(@PathVariable String idType, @PathVariable String id) {
        return getPage(idType, id, false, true, true);
    }

    /**
     *  用于设计时，基于页面id，返回页面的完整配置信息
     *
     * @param pageId
     * @return {id,type,appId,code,releaseContent,sourceContent,pageCustom,pagePermission}
     */
    @RequestMapping(value = {"/getPageById/{pageId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageById(@PathVariable String pageId) {
        return getPage("pageId", pageId, true, false, false);
    }

    /**
     *  用于设计时，基于页面的扩展id（树节点id），返回页面的完整配置信息
     *
     * @param extendId
     * @return {id,type,appId,code,releaseContent,sourceContent,pageCustom,pagePermission}
     */
    @RequestMapping(value = {"/getPageByExtendId/{extendId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageByExtendId(@PathVariable String extendId) {
        return getPage("extendId", extendId, true, false, false);
    }

    /**
     * 获取页面的多语言信息
     * 用于页面已加载后，仅切换语言时使用，只返回语言包数据
     *
     * @param idType "pageId"或"extendId"
     * @param id     id值
     * @return {pageLang: 语言包内容, locale: 当前语言}
     */
    @RequestMapping(value = {"/getPageLang/{idType}/{id}", "/getPageLang/{idType}/{id}/*"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageLang(@PathVariable String idType, @PathVariable String id) {
        try {
            String locale = getLocale();

            // 获取页面信息，以确定实际的pageId
            AppPage page = queryPage("pageId".equals(idType), id);
            if (page == null) {
                return ApiResult.fail("页面不存在或已删除！");
            }

            HashMap<String, Object> result = new HashMap<>(2);
            result.put("pageLang", queryPageLang(page.getId(), locale));
            result.put("locale", locale);

            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("获取页面多语言信息出错！", e);
            return ApiResult.fail("获取页面多语言信息出错！" + e.getMessage());
        }
    }

    /**
     * 通知页面配置更新
     * 由前端在保存完页面之后调用，通知所有客户端更新页面配置
     *
     * @param pageId 页面ID
     * @return 操作结果
     */
    @RequestMapping(value = {"/notifyUpdate/{pageId}/{extendId}"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult notifyUpdate(@PathVariable String pageId, @PathVariable String extendId) {
        try {
            EventPublisher.publish(new UpgradePageEvent(this, pageId, extendId));
            return ApiResult.success("页面更新通知已发送");
        } catch (Exception e) {
            log.error("通知页面更新出错！", e);
            return ApiResult.fail("通知页面更新出错！" + e.getMessage());
        }
    }

    /**
     * 保存页面配置
     *
     * @param appPage 页面配置对象
     * @return 操作结果
     */
    @RequestMapping(value = {"/savePage"}, method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<AppPage> savePage(AppPage appPage) {
        try {
            // 获取当前登录用户
            User user = SessionCtx.getCurrentUser();
            if (user == null) {
                return ApiResult.fail("用户未登录");
            }

            // 处理版本号，自增或初始化为1
            if (appPage.getId() != null) {
                // 从数据库获取当前版本号
                AppPage existingPage = dao.queryForObject(AppPage.class, "id", appPage.getId(), "delStatus", "0");
                if (existingPage != null) {
                    appPage.setVersion(existingPage.getVersion() + 1);
                } else {
                    appPage.setVersion(1);
                }
            } else {
                // 新页面，初始版本为1
                appPage.setVersion(1);
            }

            // 保存页面
            dao.save(appPage);

            // 保存页面日志
            AppPageLog pageLog = new AppPageLog();
            pageLog.setAppId(appPage.getAppId());
            pageLog.setPageId(appPage.getId());
            pageLog.setCode(appPage.getCode());
            pageLog.setLabel(appPage.getTitle());
            pageLog.setExtendId(appPage.getExtendId());
            pageLog.setDescription(appPage.getDescription());
            pageLog.setSourceContent(appPage.getSourceContent());
            dao.save(pageLog);

            // 清除缓存
            String pageKey = "platform_app_page_" + appPage.getId();
            String extendKey = "platform_app_page_extend_" + appPage.getExtendId();
            CacheUtil.remove(pageKey);
            CacheUtil.remove(extendKey);

            // 发布页面更新事件
            EventPublisher.publish(new UpgradePageEvent(this, appPage.getId(), appPage.getExtendId()));

            return ApiResult.success(appPage);
        } catch (Exception e) {
            log.error("保存页面配置出错！", e);
            return ApiResult.fail("保存页面配置出错！" + e.getMessage());
        }
    }

    /**
     * 获取页面配置信息
     * @param idType id类型，pageId或extendId
     * @param id pageId或extendId
     * @param withSourceContent 是否返回源代码，默认不返回
     * @param withCustomConfig 是否返回该用户此页面的自定义配置，默认不返回
     * @param withPermission 是否返回该用户此页面的权限信息，默认不返回
     * @return {id,type,appId,code,releaseContent,sourceContent,pageCustom,pagePermission,pageLang}
     */
    private ApiResult<HashMap<String, Object>> getPage(String idType, String id, Boolean withSourceContent, Boolean withCustomConfig, Boolean withPermission) {
        String locale = getLocale();
        try {
            boolean byPageId = "pageId".equals(idType);
            if (!byPageId && !"extendId".equals(idType)) {
                return ApiResult.fail("不支持的id类型" + idType);
            }
            User user = SessionCtx.getCurrentUser();

            // 设计时路径（withSourceContent=true）保持与 V1 相同的串行逻辑
            if (Boolean.TRUE.equals(withSourceContent)) {
                AppPage page = queryPage(byPageId, id);
                if (page == null) {
                    return ApiResult.fail("页面不存在或已删除！");
                }
                return ApiResult.success(buildPageMap(page, true,
                        queryPageLang(page.getId(), locale),
                        Boolean.TRUE.equals(withCustomConfig) ? queryPageCustom(page.getId(), user) : "",
                        Boolean.TRUE.equals(withPermission) ? queryPagePerms(page.getId(), page.getAppId(), user) : ""));
            }

            // 运行时路径：page 查询完成后，pageLang/pageCustom/pagePerms 三段并行组装
            // （三段均只依赖 pageId+user，pagePerms 额外需要 page.appId；page 不存在时三段自动跳过，不产生多余查询）
            // SecurityContext/DynamicDataSourceHolder 均为 ThreadLocal，不随线程池传递：
            // 每个任务先在主线程捕获用户/租户/应用，进任务时重建、结束时清理，避免池内线程残留上下文
            Tenant tenant = SecurityContext.getCurrentTenant();
            App appCtx = SecurityContext.getCurrentApp();
            CompletableFuture<AppPage> pageFuture = CompletableFuture.supplyAsync(
                    () -> withRequestContext(user, tenant, appCtx, () -> queryPage(byPageId, id)), glPageExecutor);
            CompletableFuture<Object> langFuture = pageFuture.thenApplyAsync(
                    p -> p == null ? null : withRequestContext(user, tenant, appCtx, () -> safely(() -> queryPageLang(p.getId(), locale))), glPageExecutor);
            CompletableFuture<Object> customFuture = Boolean.TRUE.equals(withCustomConfig)
                    ? pageFuture.thenApplyAsync(
                    p -> p == null ? null : withRequestContext(user, tenant, appCtx, () -> safely(() -> queryPageCustom(p.getId(), user))), glPageExecutor)
                    : CompletableFuture.completedFuture("");
            CompletableFuture<Object> permsFuture = Boolean.TRUE.equals(withPermission)
                    ? pageFuture.thenApplyAsync(
                    p -> p == null ? null : withRequestContext(user, tenant, appCtx, () -> safely(() -> queryPagePerms(p.getId(), p.getAppId(), user))), glPageExecutor)
                    : CompletableFuture.completedFuture("");

            AppPage page;
            try {
                page = pageFuture.join();
            } catch (CompletionException ce) {
                throw ce.getCause() instanceof Exception ? (Exception) ce.getCause() : ce;
            }
            CompletableFuture.allOf(langFuture, customFuture, permsFuture).join();
            if (page == null) {
                return ApiResult.fail("页面不存在或已删除！");
            }
            return ApiResult.success(buildPageMap(page, false, langFuture.join(), customFuture.join(), permsFuture.join()));
        } catch (Exception e) {
            log.error("获取页面配置信息出错！", e);
            return ApiResult.fail("获取页面配置信息出错！" + e.getMessage());
        }
    }

    /** 组装返回结构（键与 V1 一致；禁用的段返回 ""，查询无数据的段可能为 null，与 V1 语义相同） */
    private HashMap<String, Object> buildPageMap(AppPage page, boolean withSourceContent, Object pageLang, Object pageCustom, Object pagePerms) {
        HashMap<String, Object> pageMap = new HashMap<>(9);
        pageMap.put("id", page.getId());
        pageMap.put("type", page.getType());
        pageMap.put("appId", page.getAppId());
        pageMap.put("code", page.getCode());
        pageMap.put("releaseContent", toReleaseContent(page.getReleaseContent(), withSourceContent));
        pageMap.put("sourceContent", withSourceContent ? page.getSourceContent() : "");
        pageMap.put("pageLang", pageLang);
        pageMap.put("pageCustom", pageCustom);
        pageMap.put("pagePerms", pagePerms);
        return pageMap;
    }

    /**
     * 运行时路径把 releaseContent 解析为原生 JSON 对象返回：
     * 消除 JSON 字符串内嵌 JSON 的双重转义膨胀与前端二次解析。
     * 解析失败时回退为原字符串；设计时路径（withSourceContent=true）始终返回字符串。
     */
    private Object toReleaseContent(String releaseContent, boolean withSourceContent) {
        if (withSourceContent) {
            return releaseContent;
        }
        if (releaseContent == null || releaseContent.isEmpty()) {
            return releaseContent;
        }
        try {
            return objectMapper.readValue(releaseContent, Object.class);
        } catch (Exception e) {
            log.warn("releaseContent 解析为原生 JSON 失败，按原字符串返回", e);
            return releaseContent;
        }
    }

    /** 并行任务异常兜底：单段失败记日志返回 null，不阻塞整体响应 */
    private <T> T safely(Supplier<T> task) {
        try {
            return task.get();
        } catch (Exception e) {
            log.error("页面配置并行查询出错！", e);
            return null;
        }
    }

    /**
     * 在线程池任务中重建请求线程的上下文后执行任务。
     *
     * SecurityContext（用户/租户/应用）与 DynamicDataSourceHolder 均为 ThreadLocal，
     * 不会随线程池传递；而 DAO 命名 SQL 模板参数（如 $.tenantCode）与 MQL 规则解析
     * 依赖当前用户/租户，缺失会导致权限 SQL 查不到数据、租户过滤失效。
     * 任务结束后统一清理，避免池内线程残留上一个请求的上下文。
     */
    private <T> T withRequestContext(User user, Tenant tenant, App app, Supplier<T> task) {
        try {
            if (user != null) {
                SecurityContext.setCurrentUser(user);
            }
            if (tenant != null) {
                SecurityContext.setCurrentTenant(tenant);
            }
            if (app != null) {
                SecurityContext.setCurrentApp(app);
            }
            DynamicDataSourceHolder.setDataSourceKey("primary");
            return task.get();
        } finally {
            SecurityContext.clear();
            DynamicDataSourceHolder.clearDataSourceKey();
        }
    }

    /**
     * 查询页面定义（带缓存）。
     * 线程池中执行时 ThreadLocal 数据源上下文不继承，显式指定 primary
     * （与 V1 中 lang/custom/perms 段的处理方式一致）。
     */
    private AppPage queryPage(boolean byPageId, String id) {
        DynamicDataSourceHolder.setDataSourceKey("primary");
        String key = byPageId ? "platform_app_page_" + id : "platform_app_page_extend_" + id;
        if (CacheUtil.exists(key) && CacheUtil.get(key) != null) {
            return (AppPage) CacheUtil.get(key);
        }
        AppPage page = byPageId
                ? dao.queryForObject(AppPage.class, "id", id, "delStatus", "0")
                : dao.queryForObject(AppPage.class, "extendId", id, "delStatus", "0");
        if (page != null) {
            CacheUtil.put(key, page);
        }
        return page;
    }

    /** 查询页面多语言信息（带缓存，缓存空值避免重复查询） */
    private Object queryPageLang(String pageId, String locale) {
        String pageLangKey = "platform_app_page_lang_" + pageId + "_" + locale;
        if (!CacheUtil.exists(pageLangKey) || CacheUtil.get(pageLangKey) == null) {
            DynamicDataSourceHolder.setDataSourceKey("primary");
            Map<String, Object> langParams = new HashMap<>();
            langParams.put("pageId", pageId);
            langParams.put("langType", locale);
            langParams.put("delStatus", "0");
            List<AppPageLang> pageLangList = dao.queryList(AppPageLang.class, langParams, null);
            if (pageLangList != null && !pageLangList.isEmpty()) {
                CacheUtil.put(pageLangKey, pageLangList.get(0).getContent());
            } else {
                CacheUtil.put(pageLangKey, "");
            }
        }
        return CacheUtil.get(pageLangKey);
    }

    /** 查询用户对页面的自定义配置（带缓存，无数据时返回 null） */
    private Object queryPageCustom(String pageId, User user) {
        String pageCustomKey = "platform_app_page_custom_" + pageId + '_' + user.getUserId();
        if (!CacheUtil.exists(pageCustomKey) || CacheUtil.get(pageCustomKey) == null) {
            DynamicDataSourceHolder.setDataSourceKey("primary");
            Map<String, Object> pageResult = ruleService.queryForMapList("{\"platform_my_page_custom\":{\"@fs\":\"id,cfg,pageId\",\"creator|eq\":\"" + user.getUserId() + "\",\"pageId|eq\":\"" + pageId + "\",\"delStatus|eq\":0,\"@p\":\"1,1\"}}", false);
            Object dataSize = pageResult.get("dataSize");
            if (dataSize instanceof Number number && number.intValue() > 0) {
                CacheUtil.put(pageCustomKey, ((List<?>) pageResult.get("data")).get(0));
            }
        }
        return CacheUtil.get(pageCustomKey);
    }

    /** 查询用户对页面的操作权限（带缓存，无权限数据时返回 null） */
    private Object queryPagePerms(String pageId, String appId, User user) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("userId", user.getUserId());
        params.put("object", pageId);
        params.put("appId", appId);
        params.put("type", "ep");

        String pagePermissionKey = "platform_app_page_permission_" + pageId + '_' + user.getUserId();
        if (!CacheUtil.exists(pagePermissionKey) || CacheUtil.get(pagePermissionKey) == null) {
            DynamicDataSourceHolder.setDataSourceKey("primary");
            List<Map<String, Object>> permsList = dao.queryForMapList("query_permission_code_and_rule_by_role_user", params);
            if (permsList != null && !permsList.isEmpty()) {
                CacheUtil.put(pagePermissionKey, permsList);
            }
        }
        return CacheUtil.get(pagePermissionKey);
    }
}
