package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.Ctx;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.env.entity.User;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.cache.CacheUtil;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.entity.AppPage;
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
     * 排除已删除的记录
     *
     * @param idType “pageId”或“extendId”
     * @param id     id值
     * @return {id,type,appId,code,releaseContent,pageCustom}，其中pageCustom为不同用户对该页面的自定义信息
     */
    @RequestMapping(value = {"/getPageAndCustom/{idType}/{id}", "getPageAndCustom/{idType}/{id}/*"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<HashMap<String, Object>> getPageAndCustom(@PathVariable String idType, @PathVariable String id) {
        try {
            // 获取页面定义信息
            AppPage page = null;
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

            HashMap<String, Object> pageMap = new HashMap<String, Object>(6);
            if (page != null) {
                pageMap.put("id", page.getId());
                pageMap.put("type", page.getType());
                pageMap.put("appId", page.getAppId());
                pageMap.put("code", page.getCode());
                pageMap.put("releaseContent", page.getReleaseContent());

                User user = Ctx.getCurrentUser();
                // 用户自定义信息
                String pageCustomKey = "platform_app_page_custom_" + page.getId() + '_' + user.getUserId();
                if (!CacheUtil.exists(pageCustomKey) || CacheUtil.get(pageCustomKey) == null) {
                    ApiPagedResult<List<Map<String, Object>>> apiPagedResult = ruleService.queryForMapList("{\"platform_my_page_custom\":{\"@fs\":\"id,cfg,pageId\",\"creator|eq\":\"" + user.getUserId() + "\",\"pageId|eq\":\"" + page.getId() + "\",\"delStatus|eq\":0,\"@p\":\"1,1\"}}", false);
                    if (apiPagedResult.getDataSize() > 0) {
                        CacheUtil.put(pageCustomKey, ((List<?>) apiPagedResult.getData()).get(0));
                    }
                }
                pageMap.put("pageCustom", CacheUtil.get(pageCustomKey));

                // 用户对该页面的操作权限
                HashMap<String, Object> params = new HashMap<String, Object>(1);
                params.put("userId", user.getUserId());
                params.put("object", page.getId());
                params.put("appId", page.getAppId());
                params.put("type", "ep");

                String pagePermissionKey = "platform_app_page_permission_" + page.getId() + '_' + user.getUserId();
                if (!CacheUtil.exists(pagePermissionKey) || CacheUtil.get(pagePermissionKey) == null) {
                    List<Map<String, Object>> permsList = dao.queryForMapList("query_permission_code_and_rule_by_role_user", params);
                    if (permsList != null && !permsList.isEmpty()) {
                        CacheUtil.put(pagePermissionKey, permsList);
                    }
                }
                pageMap.put("pagePerms", CacheUtil.get(pagePermissionKey));

                return ApiResult.success(pageMap);
            } else {
                return ApiResult.fail("页面不存在或已删除！");
            }
        } catch (Exception e) {
            log.error("获取页面配置信息出错！", e);
            return ApiResult.fail("获取页面配置信息出错！" + e.getMessage());
        }
    }
}
