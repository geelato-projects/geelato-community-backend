package cn.geelato.web.platform.srv.base;

import cn.geelato.core.SessionCtx;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.meta.AppPage;
import cn.geelato.meta.AppPageCheckReq;
import cn.geelato.meta.enums.CheckStatusEnum;
import cn.geelato.security.User;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.platform.srv.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面签入签出控制器
 * @author itechgee@126.com
 */
@ApiRestController("/page/check")
@Slf4j
public class PageCheckController extends BaseController {

    /**
     * 页面签出/签入操作
     * @param params { pageId: string, action: 'checkout' | 'checkin' }
     * @return { code: int, status: string, msg: string, data: { checkStatus: string, checkUser: string, checkAt: string } }
     */
    @RequestMapping(value = "", method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Map<String, Object>> checkPage(@RequestBody Map<String, Object> params) {
        try {
            String pageId = (String) params.get("pageId");
            String action = (String) params.get("action");
            
            if (pageId == null || pageId.isEmpty()) {
                return ApiResult.fail("页面ID不能为空");
            }
            
            if (action == null || action.isEmpty()) {
                return ApiResult.fail("操作类型不能为空");
            }
            
            // 获取当前用户
            User currentUser = SessionCtx.getCurrentUser();
            if (currentUser == null) {
                return ApiResult.fail("用户未登录");
            }
            
            // 查询页面
            AppPage page = dao.queryForObject(AppPage.class, "id", pageId, "delStatus", "0");
            if (page == null) {
                return ApiResult.fail("页面不存在或已删除");
            }
            
            Map<String, Object> resultData = new HashMap<>(3);
            
            if ("checkout".equals(action)) {
                // 签出操作
                String checkStatus = page.getCheckStatus();
                
                if (CheckStatusEnum.CHECKED_OUT.getValue().equals(checkStatus)) {
                    // 页面已被签出
                    if (currentUser.getUserId().equals(page.getCheckUserId())) {
                        // 已被当前用户签出，直接返回成功
                        resultData.put("checkStatus", page.getCheckStatus());
                        resultData.put("checkUserId", page.getCheckUserId());
                        resultData.put("checkUserName", page.getCheckUserName());
                        resultData.put("checkAt", page.getCheckAt());
                        return ApiResult.success(resultData, "页面已被当前用户签出");
                    } else {
                        // 已被其他用户签出，返回失败
                        return ApiResult.fail("页面已被其他用户签出");
                    }
                } else {
                    // 页面未被签出，执行签出操作
                    page.setCheckStatus(CheckStatusEnum.CHECKED_OUT.getValue());
                    page.setCheckUserId(currentUser.getUserId());
                    page.setCheckUserName(currentUser.getUserName());
                    page.setCheckAt(new Date());
                    dao.save(page);
                    
                    resultData.put("checkStatus", page.getCheckStatus());
                    resultData.put("checkUserId", page.getCheckUserId());
                    resultData.put("checkUserName", page.getCheckUserName());
                    resultData.put("checkAt", page.getCheckAt());
                    return ApiResult.success(resultData, "页面签出成功");
                }
            } else if ("checkin".equals(action)) {
                // 签入操作
                String checkStatus = page.getCheckStatus();
                
                if (CheckStatusEnum.CHECKED_OUT.getValue().equals(checkStatus)) {
                    // 页面已被签出
                    if (currentUser.getUserId().equals(page.getCheckUserId())) {
                        // 已被当前用户签出，执行签入操作
                    page.setCheckStatus(CheckStatusEnum.UNCHECKED.getValue());
                    // 签入时记录当前用户作为最后签入用户，而不是清空
                    page.setCheckUserId(currentUser.getUserId());
                    page.setCheckUserName(currentUser.getUserName());
                    page.setCheckAt(new Date());
                    dao.save(page);
                        
                        resultData.put("checkStatus", page.getCheckStatus());
                        resultData.put("checkUserId", page.getCheckUserId());
                        resultData.put("checkUserName", page.getCheckUserName());
                        resultData.put("checkAt", page.getCheckAt());
                        return ApiResult.success(resultData, "页面签入成功");
                    } else {
                        // 已被其他用户签出，返回失败
                        return ApiResult.fail("页面已被其他用户签出，无法签入");
                    }
                } else {
                    // 页面未被签出，直接返回成功
                    resultData.put("checkStatus", page.getCheckStatus());
                    resultData.put("checkUserId", page.getCheckUserId());
                    resultData.put("checkUserName", page.getCheckUserName());
                    resultData.put("checkAt", page.getCheckAt());
                    return ApiResult.success(resultData, "页面未被签出");
                }
            } else {
                return ApiResult.fail("不支持的操作类型：" + action);
            }
        } catch (Exception e) {
            log.error("页面签出/签入操作失败", e);
            return ApiResult.fail("页面签出/签入操作失败：" + e.getMessage());
        }
    }

    /**
     * 获取已签出页面列表
     * @param appId 应用ID
     * @return { code: int, status: string, msg: string, data: Array<{ pageId: string, title: string, checkUser: string, checkAt: string, lastUpdateTime: string }> }
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<List<Map<String, Object>>> getCheckedOutPageList(@RequestParam String appId) {
        try {
            if (appId == null || appId.isEmpty()) {
                return ApiResult.fail("应用ID不能为空");
            }
            
            // 查询已签出的页面
            Map<String, Object> params = new HashMap<>();
            params.put("appId", appId);
            params.put("checkStatus", CheckStatusEnum.CHECKED_OUT.getValue());
            params.put("delStatus", "0");
            
            List<AppPage> pages = dao.queryList(AppPage.class, params, null);
            
            // 转换为返回格式
            List<Map<String, Object>> resultList = pages.stream().map(page -> {
                Map<String, Object> map = new HashMap<>(6);
                map.put("pageId", page.getId());
                map.put("extendId", page.getExtendId());
                map.put("title", page.getTitle());
                map.put("checkUserId", page.getCheckUserId());
                map.put("checkUserName", page.getCheckUserName());
                map.put("checkAt", page.getCheckAt());
                map.put("lastUpdateTime", page.getUpdateAt());
                return map;
            }).toList();
            
            return ApiResult.success(resultList, "获取已签出页面列表成功");
        } catch (Exception e) {
            log.error("获取已签出页面列表失败", e);
            return ApiResult.fail("获取已签出页面列表失败：" + e.getMessage());
        }
    }

    /**
     * 同步页面状态
     * @param pageId 页面ID
     * @return { code: int, status: string, msg: string, data: { checkStatus: string, checkUser: string, checkAt: string, version: string, lastUpdateTime: string } }
     */
    @RequestMapping(value = "/sync", method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Map<String, Object>> syncPageStatus(@RequestParam String pageId) {
        try {
            if (pageId == null || pageId.isEmpty()) {
                return ApiResult.fail("页面ID不能为空");
            }
            
            // 查询页面
            AppPage page = dao.queryForObject(AppPage.class, "id", pageId, "delStatus", "0");
            if (page == null) {
                return ApiResult.fail("页面不存在或已删除");
            }
            
            // 构建返回数据
            Map<String, Object> resultData = new HashMap<>(6);
            resultData.put("checkStatus", page.getCheckStatus());
            resultData.put("checkUserId", page.getCheckUserId());
            resultData.put("checkUserName", page.getCheckUserName());
            resultData.put("checkAt", page.getCheckAt());
            resultData.put("version", page.getVersion());
            resultData.put("lastUpdateTime", page.getUpdateAt());
            
            return ApiResult.success(resultData, "同步页面状态成功");
        } catch (Exception e) {
            log.error("同步页面状态失败", e);
            return ApiResult.fail("同步页面状态失败：" + e.getMessage());
        }
    }

    /**
     * 申请接管页面
     * @param params { pageId: string, targetUser: string }
     * @return { code: int, status: string, msg: string, data: { requestId: string } }
     */
    @RequestMapping(value = "/request", method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Map<String, Object>> requestPageCheckOut(@RequestBody Map<String, Object> params) {
        try {
            String pageId = (String) params.get("pageId");
            String targetUser = (String) params.get("targetUser");
            
            if (pageId == null || pageId.isEmpty()) {
                return ApiResult.fail("页面ID不能为空");
            }
            
            if (targetUser == null || targetUser.isEmpty()) {
                return ApiResult.fail("目标用户ID不能为空");
            }
            
            // 获取当前用户
            User currentUser = SessionCtx.getCurrentUser();
            if (currentUser == null) {
                return ApiResult.fail("用户未登录");
            }
            
            // 查询页面
            AppPage page = dao.queryForObject(AppPage.class, "id", pageId, "delStatus", "0");
            if (page == null) {
                return ApiResult.fail("页面不存在或已删除");
            }
            
            // 检查页面是否已有待处理的接管请求
            Map<String, Object> requestParams = new HashMap<>();
            requestParams.put("pageId", pageId);
            requestParams.put("status", "pending");
            List<AppPageCheckReq> existingRequests = dao.queryList(AppPageCheckReq.class, requestParams, null);
            
            if (!existingRequests.isEmpty()) {
                return ApiResult.fail("该页面已有待处理的接管申请");
            }
            
            // 创建接管请求
            AppPageCheckReq request = new AppPageCheckReq();
            request.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
            request.setPageId(pageId);
            request.setPageTitle(page.getTitle());
            request.setRequesterId(currentUser.getUserId());
            request.setRequesterName(currentUser.getUserName());
            request.setTargetUserId(targetUser);
            // 这里需要根据targetUser获取目标用户名称，暂时使用占位符
            request.setTargetUserName("目标用户");
            request.setRequestTime(new Date());
            request.setStatus("pending");
            request.setCreateAt(new Date());
            request.setUpdateAt(new Date());
            
            // 保存接管请求
            dao.save(request);
            
            // 这里可以添加Sse通知逻辑，通知目标用户
            
            Map<String, Object> resultData = new HashMap<>(1);
            resultData.put("requestId", request.getId());
            
            return ApiResult.success(resultData, "申请接管页面成功");
        } catch (Exception e) {
            log.error("申请接管页面失败", e);
            return ApiResult.fail("申请接管页面失败：" + e.getMessage());
        }
    }

    /**
     * 获取接管请求列表
     * @param userId 用户ID
     * @param status 请求状态（可选）
     * @return { code: int, status: string, msg: string, data: Array<CheckRequest> }
     */
    @RequestMapping(value = "/request/list", method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<List<AppPageCheckReq>> getCheckRequestList(@RequestParam String userId, @RequestParam(required = false) String status) {
        try {
            if (userId == null || userId.isEmpty()) {
                return ApiResult.fail("用户ID不能为空");
            }
            
            // 查询接管请求
            Map<String, Object> params = new HashMap<>();
            params.put("targetUserId", userId);
            if (status != null && !status.isEmpty()) {
                params.put("status", status);
            }
            
            List<AppPageCheckReq> requests = dao.queryList(AppPageCheckReq.class, params, null);
            
            return ApiResult.success(requests, "获取接管请求列表成功");
        } catch (Exception e) {
            log.error("获取接管请求列表失败", e);
            return ApiResult.fail("获取接管请求列表失败：" + e.getMessage());
        }
    }

    /**
     * 处理接管请求
     * @param params { requestId: string, action: 'approve' | 'reject', comment?: string }
     * @return { code: int, status: string, msg: string, data: { pageId: string, checkStatus: string } }
     */
    @RequestMapping(value = "/request/process", method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Map<String, Object>> processCheckRequest(@RequestBody Map<String, Object> params) {
        try {
            String requestId = (String) params.get("requestId");
            String action = (String) params.get("action");
            String comment = (String) params.get("comment");
            
            if (requestId == null || requestId.isEmpty()) {
                return ApiResult.fail("请求ID不能为空");
            }
            
            if (action == null || action.isEmpty()) {
                return ApiResult.fail("操作类型不能为空");
            }
            
            // 获取当前用户
            User currentUser = SessionCtx.getCurrentUser();
            if (currentUser == null) {
                return ApiResult.fail("用户未登录");
            }
            
            // 查询接管请求
            AppPageCheckReq request = dao.queryForObject(AppPageCheckReq.class, "id", requestId);
            if (request == null) {
                return ApiResult.fail("接管请求不存在或已删除");
            }
            
            if (!"pending".equals(request.getStatus())) {
                return ApiResult.fail("只有待处理的接管请求才能被处理");
            }
            
            // 更新接管请求状态
            request.setProcessTime(new Date());
            request.setProcessUserId(currentUser.getUserId());
            request.setProcessUserName(currentUser.getUserName());
            request.setProcessComment(comment);
            request.setUpdateAt(new Date());
            
            Map<String, Object> resultData = new HashMap<>(2);
            resultData.put("pageId", request.getPageId());
            
            if ("approve".equals(action)) {
                // 批准请求
                request.setStatus("approved");
                
                // 查询页面并签入
                AppPage page = dao.queryForObject(AppPage.class, "id", request.getPageId(), "delStatus", "0");
                if (page != null && CheckStatusEnum.CHECKED_OUT.getValue().equals(page.getCheckStatus())) {
                    page.setCheckStatus(CheckStatusEnum.UNCHECKED.getValue());
                    // 签入时记录当前用户作为最后签入用户，而不是清空
                    page.setCheckUserId(currentUser.getUserId());
                    page.setCheckUserName(currentUser.getUserName());
                    page.setCheckAt(new Date());
                    dao.save(page);
                }
                
                resultData.put("checkStatus", CheckStatusEnum.UNCHECKED.getValue());
            } else if ("reject".equals(action)) {
                // 拒绝请求
                request.setStatus("rejected");
                
                // 查询页面获取当前状态
                AppPage page = dao.queryForObject(AppPage.class, "id", request.getPageId(), "delStatus", "0");
                if (page != null) {
                    resultData.put("checkStatus", page.getCheckStatus());
                } else {
                    resultData.put("checkStatus", "");
                }
            } else {
                return ApiResult.fail("不支持的操作类型：" + action);
            }
            
            // 保存接管请求
            dao.save(request);
            
            // 这里可以添加Sse通知逻辑，通知发起用户
            
            return ApiResult.success(resultData, "处理接管请求成功");
        } catch (Exception e) {
            log.error("处理接管请求失败", e);
            return ApiResult.fail("处理接管请求失败：" + e.getMessage());
        }
    }

    /**
     * 取消接管请求
     * @param params { requestId: string }
     * @return { code: int, status: string, msg: string, data: null }
     */
    @RequestMapping(value = "/request/cancel", method = RequestMethod.POST, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult cancelCheckRequest(@RequestBody Map<String, Object> params) {
        try {
            String requestId = (String) params.get("requestId");
            
            if (requestId == null || requestId.isEmpty()) {
                return ApiResult.fail("请求ID不能为空");
            }
            
            // 获取当前用户
            User currentUser = SessionCtx.getCurrentUser();
            if (currentUser == null) {
                return ApiResult.fail("用户未登录");
            }
            
            // 查询接管请求
            AppPageCheckReq request = dao.queryForObject(AppPageCheckReq.class, "id", requestId);
            if (request == null) {
                return ApiResult.fail("接管请求不存在或已删除");
            }
            
            if (!"pending".equals(request.getStatus())) {
                return ApiResult.fail("只有待处理的接管请求才能被取消");
            }
            
            if (!currentUser.getUserId().equals(request.getRequesterId())) {
                return ApiResult.fail("只有发起用户才能取消接管请求");
            }
            
            // 更新接管请求状态为已取消
            request.setStatus("rejected");
            request.setProcessTime(new Date());
            request.setProcessUserId(currentUser.getUserId());
            request.setProcessUserName(currentUser.getUserName());
            request.setProcessComment("用户取消了接管请求");
            request.setUpdateAt(new Date());
            
            // 保存接管请求
            dao.save(request);
            
            return ApiResult.success("取消接管请求成功");
        } catch (Exception e) {
            log.error("取消接管请求失败", e);
            return ApiResult.fail("取消接管请求失败：" + e.getMessage());
        }
    }
}
