package cn.geelato.web.platform.weixin.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.HttpUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.meta.User;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.weixin.config.WeixinWorkConfigurationProperties;
import cn.geelato.web.platform.weixin.mapper.WeixinWorkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController("/weixin/work")
@Slf4j
public class WeixinWorkController extends BaseController {



    @Autowired
    private WeixinWorkMapper weixinWorkMapper;
    
    @Autowired
    private WeixinWorkConfigurationProperties weixinWorkConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 访问令牌缓存
    private volatile String cachedAccessToken;
    // 访问令牌过期时间戳
    private volatile long tokenExpireTime;

    @RequestMapping(value = "/syncUser", method = RequestMethod.GET)
    public ApiResult<?> syncContacts(@RequestParam(required = false) String orgId) {
        int successCount = 0;
        List<User> users;
        if (orgId != null && !orgId.isEmpty()) {
            users = weixinWorkMapper.findUsersByOrgId(orgId);
        } else {
            users = weixinWorkMapper.findAllUsers();
        }

        // 2. 遍历用户，根据手机号获取企业微信userId
        for (User user : users) {
            String mobilePhone = user.getMobilePhone();
            String existingWeixinWorkUserId = user.getWeixinWorkUserId();

            // 只有当用户的weixinWorkUserId为空且手机号不为空时才进行同步
            if ((existingWeixinWorkUserId == null || existingWeixinWorkUserId.isEmpty())
                    && mobilePhone != null && !mobilePhone.isEmpty()) {
                String weixinWorkUserId = getUserIdByMobile(mobilePhone);

                // 3. 将userId写入platform_user表的weixin_work_userId字段
                if (weixinWorkUserId != null) {
                    weixinWorkMapper.updateUserWeixinWorkUserId(weixinWorkUserId, user.getId());
                    successCount++;
                }
            }
        }

        return ApiResult.success(null, "成功同步" + successCount + "个用户");
    }


    @RequestMapping(value = "/queryGroup", method = RequestMethod.GET)
    public ApiResult<?> queryGroup(@RequestParam(required = false) String userId) {
        try {
            // 如果userId为空，则使用当前用户ID
            if (userId == null || userId.isEmpty()) {
                userId = SecurityContext.getCurrentUser().getUserId();
            }
            
            // 1. 通过userId获取对应用户的企业微信id
            String weixinWorkUserId = weixinWorkMapper.getWeixinWorkUserIdByUserId(userId);
            if (weixinWorkUserId == null || weixinWorkUserId.isEmpty()) {
                return ApiResult.fail("用户未绑定企业微信账号");
            }
            
            // 2. 通过企业微信userId获取客户群组列表
            String listUrl = weixinWorkConfig.getApiBaseUrl() + "/externalcontact/groupchat/list?access_token=" +  getAccessToken();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("status_filter", 0); // 0-正常 1-跟进人离职 2-离职继承中 3-离职继承完成
            requestBody.put("owner_filter", new HashMap<String, Object>() {{
                put("userid_list", new String[]{weixinWorkUserId});
            }});
            requestBody.put("offset", 0);
            requestBody.put("limit", 1000);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = HttpUtils.doPost(listUrl, jsonBody, null);
            
            JsonNode root = objectMapper.readTree(responseBody);
            int errcode = root.path("errcode").asInt();
            
            if (errcode != 0) {
                String errmsg = root.path("errmsg").asText();
                log.error("获取客户群组列表失败: {} - {}", errcode, errmsg);
                return ApiResult.fail("获取客户群组列表失败: " + errmsg);
            }
            
            // 3. 获取群组详情列表
            JsonNode groupChatList = root.path("group_chat_list");
            List<Map<String, Object>> groupDetails = new ArrayList<>();
            
            for (JsonNode groupChat : groupChatList) {
                String chatId = groupChat.path("chat_id").asText();
                if (chatId != null && !chatId.isEmpty()) {
                    // 获取群组详情
                    Map<String, Object> groupDetail = getGroupChatDetail(chatId);
                    groupDetails.add(groupDetail);
                }
            }
            
            return ApiResult.success(groupDetails);
        } catch (Exception e) {
            log.error("获取客户群组列表异常", e);
            return ApiResult.fail("获取客户群组列表异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取群组详情
     * @param chatId 群组ID
     * @return 群组详情，包含chat_id和群组名称
     */
    private Map<String, Object> getGroupChatDetail(String chatId) {
        try {
            String detailUrl = weixinWorkConfig.getApiBaseUrl() + "/externalcontact/groupchat/get?access_token=" + getAccessToken();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("need_name", 1); // 是否需要返回群成员的名字
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = HttpUtils.doPost(detailUrl, jsonBody, null);
            
            JsonNode root = objectMapper.readTree(responseBody);
            int errcode = root.path("errcode").asInt();
            
            if (errcode == 0) {
                JsonNode groupChat = root.path("group_chat");
                Map<String, Object> result = new HashMap<>();
                result.put("chat_id", chatId);
                result.put("name", groupChat.path("name").asText());
                result.put("owner", groupChat.path("owner").asText());
                result.put("create_time", groupChat.path("create_time").asLong());
                result.put("notice", groupChat.path("notice").asText());
                result.put("member_count", groupChat.path("member_list").size());
                
                return result;
            } else {
                String errmsg = root.path("errmsg").asText();
                log.warn("获取群组详情失败 chatId={}: {} - {}", chatId, errcode, errmsg);
                
                // 即使获取详情失败，也返回基本信息
                Map<String, Object> result = new HashMap<>();
                result.put("chat_id", chatId);
                result.put("name", "获取群名失败");
                return result;
            }
        } catch (Exception e) {
            log.error("获取群组详情异常 chatId={}", chatId, e);
            
            // 异常情况下返回基本信息
            Map<String, Object> result = new HashMap<>();
            result.put("chat_id", chatId);
            result.put("name", "获取群名异常");
            return result;
        }
    }
    

    @RequestMapping(value = "/getUserByMobilePhone", method = RequestMethod.GET)
    public ApiResult<?> getUserByMobilePhone(@RequestParam String mobilePhone) {
        try {
            String weixinWorkUserId = getUserIdByMobile(mobilePhone);
            if (weixinWorkUserId != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("userId", weixinWorkUserId);
                result.put("mobile", mobilePhone);
                return ApiResult.success(result);
            } else {
                return ApiResult.fail("未找到对应的企业微信用户");
            }
        } catch (Exception e) {
            log.error("通过手机号获取企业微信用户ID异常", e);
            return ApiResult.fail("通过手机号获取企业微信用户ID异常: " + e.getMessage());
        }
    }
    

    @RequestMapping(value = "/queryOrg", method = RequestMethod.GET)
    public ApiResult<?> queryOrg() {
        try {
            String url = weixinWorkConfig.getApiBaseUrl() + "/department/list?access_token=" + getAccessToken();
            
            String responseBody = HttpUtils.doGet(url, null);
            
            JsonNode root = objectMapper.readTree(responseBody);
            int errcode = root.path("errcode").asInt();
            
            if (errcode == 0) {
                return ApiResult.success(root.path("department"));
            } else {
                String errmsg = root.path("errmsg").asText();
                log.error("获取部门列表失败: {} - {}", errcode, errmsg);
                return ApiResult.fail("获取部门列表失败: " + errmsg);
            }
        } catch (Exception e) {
            log.error("获取部门列表异常", e);
            return ApiResult.fail("获取部门列表异常: " + e.getMessage());
        }
    }
    

    @RequestMapping(value = "/queryUserByOrg", method = RequestMethod.GET)
    public ApiResult<?> queryUserByOrg(@RequestParam String departmentId, 
                                       @RequestParam(defaultValue = "1") Integer fetchChild) {
        try {
            String url = weixinWorkConfig.getApiBaseUrl() + "/user/list?access_token=" +  getAccessToken() +
                        "&department_id=" + departmentId + "&fetch_child=" + fetchChild;
            
            String responseBody = HttpUtils.doGet(url, null);
            
            JsonNode root = objectMapper.readTree(responseBody);
            int errcode = root.path("errcode").asInt();
            
            if (errcode == 0) {
                return ApiResult.success(root.path("userlist"));
            } else {
                String errmsg = root.path("errmsg").asText();
                log.error("获取部门用户列表失败: {} - {}", errcode, errmsg);
                return ApiResult.fail("获取部门用户列表失败: " + errmsg);
            }
        } catch (Exception e) {
            log.error("获取部门用户列表异常", e);
            return ApiResult.fail("获取部门用户列表异常: " + e.getMessage());
        }
    }



    /**
     * 获取企业微信访问令牌（带缓存机制）
     * @return 访问令牌
     */
    private String getAccessToken() {
        // 检查缓存是否有效
        long currentTime = System.currentTimeMillis();
        if (cachedAccessToken != null && currentTime < tokenExpireTime) {
            log.debug("使用缓存的访问令牌，剩余有效时间: {} 秒", (tokenExpireTime - currentTime) / 1000);
            return cachedAccessToken;
        }

        // 缓存无效或不存在，重新获取
        synchronized (this) {
            // 双重检查锁定，防止并发情况下重复获取
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
                return cachedAccessToken;
            }

            try {
                String corpId = SecurityContext.getCurrentTenant().getConfiguration().getOrDefault("corpId", "wwfed14f2fada336fd").toString();
                String corpSecret = SecurityContext.getCurrentTenant().getConfiguration().getOrDefault("corpSecret", "Pm4fjHTNfP5epQ4Ai5w0zJ_1OD8V4-HQXkJcz1xkOOY").toString();
                String url = weixinWorkConfig.getApiBaseUrl() + "/gettoken?corpid=" + corpId + "&corpsecret=" + corpSecret;
                
                log.info("正在获取企业微信访问令牌...");
                String responseBody = HttpUtils.doGet(url, null);

                JsonNode root = objectMapper.readTree(responseBody);
                int errcode = root.path("errcode").asInt();

                if (errcode == 0) {
                    String accessToken = root.path("access_token").asText();
                    int expiresIn = root.path("expires_in").asInt(7200); // 默认7200秒
                    
                    // 更新缓存，提前一些时间过期以避免边界情况
                    cachedAccessToken = accessToken;
                    tokenExpireTime = System.currentTimeMillis() + (expiresIn - 200) * 1000L; // 提前200秒过期
                    
                    log.info("企业微信访问令牌获取成功，有效期: {} 秒", expiresIn);
                    return accessToken;
                } else {
                    String errmsg = root.path("errmsg").asText();
                    log.error("获取企业微信访问令牌失败: {} - {}", errcode, errmsg);
                    throw new RuntimeException("获取企业微信访问令牌失败: " + errmsg);
                }
            } catch (Exception e) {
                log.error("获取企业微信访问令牌异常", e);
                throw new RuntimeException("获取企业微信访问令牌异常", e);
            }
        }
    }


    /**
     * 获取企业微信访问令牌（公开方法）
     * @return 访问令牌
     */
    @RequestMapping(value = "/getToken", method = RequestMethod.GET)
    public ApiResult<?> getToken() {
        try {
            String accessToken = getAccessToken();
            Map<String, Object> result = new HashMap<>();
            result.put("access_token", accessToken);
            result.put("cache_expire_time", tokenExpireTime);
            result.put("is_from_cache", cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("获取访问令牌异常", e);
            return ApiResult.fail("获取访问令牌异常: " + e.getMessage());
        }
    }
    
    /**
     * 清除访问令牌缓存
     * @return 操作结果
     */
    @RequestMapping(value = "/clearTokenCache", method = RequestMethod.POST)
    public ApiResult<?> clearTokenCache() {
        try {
            cachedAccessToken = null;
            tokenExpireTime = 0;
            log.info("企业微信访问令牌缓存已清除");
            return ApiResult.success("访问令牌缓存已清除");
        } catch (Exception e) {
            log.error("清除访问令牌缓存异常", e);
            return ApiResult.fail("清除访问令牌缓存异常: " + e.getMessage());
        }
    }

    private String getUserIdByMobile(String mobile) {
        try {
            String accessToken = getAccessToken();
            String url = weixinWorkConfig.getApiBaseUrl() + "/user/getuserid?access_token=" + accessToken;

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("mobile", mobile);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = HttpUtils.doPost(url, jsonBody, null);

            JsonNode root = objectMapper.readTree(responseBody);
            int errcode = root.path("errcode").asInt();

            if (errcode == 0) {
                return root.path("userid").asText();
            } else {
                String errmsg = root.path("errmsg").asText();
                log.error("根据手机号获取企业微信用户ID失败: {} - {}", errcode, errmsg);
                return null; // 返回null而不是抛出异常，因为可能有些用户在平台中存在但在企业微信中不存在
            }
        } catch (Exception e) {
            log.error("根据手机号获取企业微信用户ID异常", e);
            return null;
        }
    }

}