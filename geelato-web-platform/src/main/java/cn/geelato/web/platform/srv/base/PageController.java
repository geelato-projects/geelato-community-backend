package cn.geelato.web.platform.srv.base;

import cn.geelato.core.SessionCtx;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.security.User;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.utils.CacheUtil;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.AppPage;
import cn.geelato.meta.AppPageLang;
import cn.geelato.web.platform.event.UpgradePageEvent;
import cn.geelato.web.common.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author itechgee@126.com
 */
@ApiRestController("/page")
@Slf4j
public class PageController extends BaseController {

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
        return getPage(idType, id, false,true,true);
    }

    /**
     *  用于设计时，基于页面id，返回页面的完整配置信息
     *
     * @param pageId
     * @return {id,type,appId,code,releaseContent,sourceContent,pageCustom,pagePermission}
     */
    @RequestMapping(value = {"/getPageById/{pageId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageById(@PathVariable String pageId) {
        return getPage("pageId", pageId,  true,false,false);
    }

    /**
     *  用于设计时，基于页面的扩展id（树节点id），返回页面的完整配置信息
     *
     * @param extendId
     * @return {id,type,appId,code,releaseContent,sourceContent,pageCustom,pagePermission}
     */
    @RequestMapping(value = {"/getPageByExtendId/{extendId}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageByExtendId(@PathVariable String extendId) {
        return getPage("extendId", extendId, true,false,false);
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
            AppPage page;
            if ("pageId".equals(idType)) {
                String key = "platform_app_page_" + id;
                if (CacheUtil.exists(key) && CacheUtil.get(key) != null) {
                    page = (AppPage) CacheUtil.get(key);
                } else {
                    page = dao.queryForObject(AppPage.class, "id", id, "delStatus", "0");
                    if (page != null) {
                        CacheUtil.put(key, page);
                    }
                }
            } else if ("extendId".equals(idType)) {
                String key = "platform_app_page_extend_" + id;
                if (CacheUtil.exists(key) && CacheUtil.get(key) != null) {
                    page = (AppPage) CacheUtil.get(key);
                } else {
                    page = dao.queryForObject(AppPage.class, "extendId", id, "delStatus", "0");
                    if (page != null) {
                        CacheUtil.put(key, page);
                    }
                }
            } else {
                return ApiResult.fail("不支持的id类型" + idType);
            }

            if (page == null) {
                return ApiResult.fail("页面不存在或已删除！");
            }

            HashMap<String, Object> result = new HashMap<>(2);
            
            // 获取页面多语言信息
            String pageLangKey = "platform_app_page_lang_" + page.getId() + "_" + locale;
            if (!CacheUtil.exists(pageLangKey) || CacheUtil.get(pageLangKey) == null) {
                //todo 线程池问题临时处理方法
                DynamicDataSourceHolder.setDataSourceKey("primary");
                // 构建查询条件
                Map<String, Object> langParams = new HashMap<>();
                langParams.put("pageId", page.getId());
                langParams.put("langType", locale);
                langParams.put("delStatus", "0");
                List<AppPageLang> pageLangList = dao.queryList(AppPageLang.class, langParams, null);
                if (pageLangList != null && !pageLangList.isEmpty()) {
                    CacheUtil.put(pageLangKey, pageLangList.get(0).getContent());
                } else {
                    // 缓存空值，避免重复查询
                    CacheUtil.put(pageLangKey, "");
                }
            }
            result.put("pageLang", CacheUtil.get(pageLangKey));
            result.put("locale", locale);
            
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("获取页面多语言信息出错！", e);
            return ApiResult.fail("获取页面多语言信息出错！" + e.getMessage());
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
    private ApiResult<HashMap<String, Object>> getPage(String idType,String id,Boolean withSourceContent,Boolean withCustomConfig,Boolean withPermission) {
        String locale = getLocale();
        try {
            // 获取页面定义信息
            AppPage page;
            if ("pageId".equals(idType)) {
                String key = "platform_app_page_" + id;
                if (CacheUtil.exists(key) && CacheUtil.get(key) != null) {
                    page = (AppPage) CacheUtil.get(key);
                } else {
                    page = dao.queryForObject(AppPage.class, "id", id, "delStatus", "0");
                    if (page != null) {
                        CacheUtil.put(key, page);
                    }
                }
            } else if ("extendId".equals(idType)) {
                String key = "platform_app_page_extend_" + id;
                if (CacheUtil.exists(key) && CacheUtil.get(key) != null) {
                    page = (AppPage) CacheUtil.get(key);
                } else {
                    page = dao.queryForObject(AppPage.class, "extendId", id, "delStatus", "0");
                    if (page != null) {
                        CacheUtil.put(key, page);
                    }
                }
            } else {
                return ApiResult.fail("不支持的id类型" + idType);
            }

            HashMap<String, Object> pageMap = new HashMap<String, Object>(7);
            if (page != null) {
                pageMap.put("id", page.getId());
                pageMap.put("type", page.getType());
                pageMap.put("appId", page.getAppId());
                pageMap.put("code", page.getCode());
                pageMap.put("releaseContent", page.getReleaseContent());
                if(withSourceContent){
                    pageMap.put("sourceContent", page.getSourceContent());
                }else{
                    pageMap.put("sourceContent", "");
                }

                // 获取页面多语言信息
                String pageLangKey = "platform_app_page_lang_" + page.getId() + "_" + locale;
                if (!CacheUtil.exists(pageLangKey) || CacheUtil.get(pageLangKey) == null) {
                    //todo 线程池问题临时处理方法
                    DynamicDataSourceHolder.setDataSourceKey("primary");
                    // 构建查询条件
                    Map<String, Object> langParams = new HashMap<>();
                    langParams.put("pageId", page.getId());
                    langParams.put("langType", locale);
                    langParams.put("delStatus", "0");
                    List<AppPageLang> pageLangList = dao.queryList(AppPageLang.class, langParams, null);
                    if (pageLangList != null && !pageLangList.isEmpty()) {
                        CacheUtil.put(pageLangKey, pageLangList.get(0).getContent());
                    } else {
                        // 缓存空值，避免重复查询
                        CacheUtil.put(pageLangKey, "");
                    }
                }
                pageMap.put("pageLang", CacheUtil.get(pageLangKey));

                User user = SessionCtx.getCurrentUser();
                // 用户自定义信息
                if(withCustomConfig){
                    String pageCustomKey = "platform_app_page_custom_" + page.getId() + '_' + user.getUserId();
                    if (!CacheUtil.exists(pageCustomKey) || CacheUtil.get(pageCustomKey) == null) {
                        //todo 线程池问题临时处理方法
                        DynamicDataSourceHolder.setDataSourceKey("primary");
                        ApiPagedResult<List<Map<String, Object>>> apiPagedResult = ruleService.queryForMapList("{\"platform_my_page_custom\":{\"@fs\":\"id,cfg,pageId\",\"creator|eq\":\"" + user.getUserId() + "\",\"pageId|eq\":\"" + page.getId() + "\",\"delStatus|eq\":0,\"@p\":\"1,1\"}}", false);
                        if (apiPagedResult.getDataSize() > 0) {
                            CacheUtil.put(pageCustomKey, ((List<?>) apiPagedResult.getData()).get(0));
                        }
                    }
                    pageMap.put("pageCustom", CacheUtil.get(pageCustomKey));
                }else{
                    pageMap.put("pageCustom", "");
                }


                // 用户对该页面的操作权限
                if(withPermission){
                    HashMap<String, Object> params = new HashMap<String, Object>(1);
                    params.put("userId", user.getUserId());
                    params.put("object", page.getId());
                    params.put("appId", page.getAppId());
                    params.put("type", "ep");

                    String pagePermissionKey = "platform_app_page_permission_" + page.getId() + '_' + user.getUserId();
                    if (!CacheUtil.exists(pagePermissionKey) || CacheUtil.get(pagePermissionKey) == null) {
                        //todo 线程池问题临时处理方案
                        DynamicDataSourceHolder.setDataSourceKey("primary");
                        List<Map<String, Object>> permsList = dao.queryForMapList("query_permission_code_and_rule_by_role_user", params);
                        if (permsList != null && !permsList.isEmpty()) {
                            CacheUtil.put(pagePermissionKey, permsList);
                        }
                    }
                    pageMap.put("pagePerms", CacheUtil.get(pagePermissionKey));
                }else {
                    pageMap.put("pagePerms", "");
                }


                return ApiResult.success(pageMap);
            } else {
                return ApiResult.fail("页面不存在或已删除！");
            }
        } catch (Exception e) {
            log.error("获取页面配置信息出错！", e);
            return ApiResult.fail("获取页面配置信息出错！" + e.getMessage());
        }
    }

    /**
     * 通知页面配置更新
     * 由前端在保存完页面之后调用，通知所有客户端更新页面配置
     *
     * @param pageId 页面ID
     * @return 操作结果
     */
    @RequestMapping(value = {"/notifyUpdate/{pageId}/{extendId}"}, method = {RequestMethod.GET,RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult notifyUpdate(@PathVariable String pageId, @PathVariable String extendId) {
        try {
            EventPublisher.publish(new UpgradePageEvent(this, pageId,extendId));
            return ApiResult.success("页面更新通知已发送");
        } catch (Exception e) {
            log.error("通知页面更新出错！", e);
            return ApiResult.fail("通知页面更新出错！" + e.getMessage());
        }
    }

}