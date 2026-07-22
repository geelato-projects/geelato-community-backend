package cn.geelato.web.platform.srv.weixin;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Company;
import cn.geelato.security.*;
import cn.geelato.utils.HttpUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.boot.properties.WeixinWorkConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.service.WeixinWorkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController("/weixin/work")
@Slf4j
public class WeixinWorkController extends BaseController {
    @Autowired
    private WeixinWorkService weixinWorkService;

    @Autowired
    private UserProvider userProvider;

    @Autowired
    private WeixinWorkConfigurationProperties weixinWorkConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String cachedAccessToken;
    private volatile long tokenExpireTime;
    @RequestMapping(value = "/syncUser", method = RequestMethod.GET)
    public ApiResult<?> syncContacts(@RequestParam String orgId) {
        try {
            // 从 platform_company 表获取企业微信配置信息
            String corpId = null;
            String corpSecret = null;
            
            if (orgId != null && !orgId.isEmpty()) {
                // 获取指定组织的企业微信配置
                String weixinWorkInfo = weixinWorkService.getCompanyWeixinWorkInfo(orgId);
                if (weixinWorkInfo != null && !weixinWorkInfo.isEmpty()) {
                    try {
                        JsonNode configNode = objectMapper.readTree(weixinWorkInfo);
                        corpId = configNode.path("corpId").asText(null);
                        corpSecret = configNode.path("corpSecret").asText(null);
                    } catch (Exception e) {
                        log.error("解析企业微信配置信息失败", e);
                        return ApiResult.fail("企业微信配置信息格式错误: " + e.getMessage());
                    }
                }
            } 

            if (corpId == null || corpId.isEmpty() || corpSecret == null || corpSecret.isEmpty()) {
                return ApiResult.fail("企业微信配置信息不完整，无法同步用户");
            }
            
            int successCount = 0;
            List<User> users;
            users = weixinWorkService.findUsersByOrgId(orgId);

            for (User user : users) {
                String mobilePhone = user.getMobilePhone();
                String existingWeixinWorkUserId = user.getWeixinWorkUserId();

                if ((existingWeixinWorkUserId == null || existingWeixinWorkUserId.isEmpty())
                        && mobilePhone != null && !mobilePhone.isEmpty()) {
                    String weixinWorkUserId = getUserIdByMobile(mobilePhone, corpId, corpSecret);

                    if (weixinWorkUserId != null) {
                        weixinWorkService.updateUserWeixinWorkUserId(weixinWorkUserId, user.getId());
                        successCount++;
                    }
                }
            }

            return ApiResult.success(null, "成功同步" + successCount + "个用户");
        } catch (Exception e) {
            log.error("同步用户失败", e);
            return ApiResult.fail("同步用户失败: " + e.getMessage());
        }
    }


    @RequestMapping(value = "/queryGroup", method = RequestMethod.GET)
    public ApiResult<?> queryGroup(@RequestParam(required = false) String userId) {
        try {
            String companyId;
            boolean filter;
            if (userId == null || userId.isEmpty()) {
                filter = !SecurityContext.isAdmin();
                userId = SecurityContext.getCurrentUser().getUserId();
                companyId = SecurityContext.getCurrentUser().getCompanyId();
            }else{
                filter = true;
                companyId=userProvider.getUserById(userId).getCompanyId();
            }

            String weixinWorkUserId = weixinWorkService.getWeixinWorkUserIdByUserId(userId);
            if (weixinWorkUserId == null || weixinWorkUserId.isEmpty()) {
                return ApiResult.fail("用户未绑定企业微信账号");
            }
            
            // 从platform_company表获取企业微信配置信息
            String weixinWorkInfo = weixinWorkService.getCompanyWeixinWorkInfo(companyId);
            if (weixinWorkInfo == null || weixinWorkInfo.isEmpty()) {
                log.error("未找到公司的企业微信配置信息, companyId: {}", companyId);
                return ApiResult.fail("未找到公司的企业微信配置信息");
            }
            
            // 解析企业微信配置信息
            JsonNode weixinWorkConfig = objectMapper.readTree(weixinWorkInfo);
            String corpId = weixinWorkConfig.path("corpId").asText();
            String corpSecret = weixinWorkConfig.path("corpSecret").asText();
            
            if (corpId == null || corpId.isEmpty() || corpSecret == null || corpSecret.isEmpty()) {
                log.error("企业微信配置信息不完整, corpId或corpSecret为空, companyId: {}", companyId);
                return ApiResult.fail("企业微信配置信息不完整");
            }

            String listUrl = this.weixinWorkConfig.getApiBaseUrl() +
                    "/externalcontact/groupchat/list?access_token=" + getAccessToken(corpId, corpSecret);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("status_filter", 0);
            if (filter) {
                requestBody.put("owner_filter", new HashMap<String, Object>() {{
                    put("userid_list", new String[]{weixinWorkUserId});
                }});
            }
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

            JsonNode groupChatList = root.path("group_chat_list");
            List<Map<String, Object>> groupList = new ArrayList<>();
            for (JsonNode groupChat : groupChatList) {
                String chatId = groupChat.path("chat_id").asText();
                if (chatId == null || chatId.isEmpty()) {
                    continue;
                }

                Map<String, Object> groupDetailResult = getGroupChatDetail(chatId, corpId, corpSecret);
                Map<String, Object> groupSummary = new HashMap<>();
                groupSummary.put("chat_id", chatId);

                Object groupChatDetail = groupDetailResult.get("group_chat");
                if (groupChatDetail instanceof Map) {
                    Map<?, ?> groupChatMap = (Map<?, ?>) groupChatDetail;
                    groupSummary.put("name", groupChatMap.get("name"));
                    groupSummary.put("owner", groupChatMap.get("owner"));
                } else {
                    groupSummary.put("name", null);
                    groupSummary.put("owner", null);
                }
                groupList.add(groupSummary);
            }

            return ApiResult.success(groupList);
        } catch (Exception e) {
            log.error("获取客户群组列表异常", e);
            return ApiResult.fail("获取客户群组列表异常: " + e.getMessage());
        }
    }

    @RequestMapping(value = "/getGroupDetail", method = RequestMethod.GET)
    public ApiResult<?> getGroupDetail(@RequestParam String chatId,
                                       @RequestParam(required = false) String userId) {
        try {
            if (chatId == null || chatId.isEmpty()) {
                return ApiResult.fail("群组Id不能为空");
            }

            String companyId;
            if (userId == null || userId.isEmpty()) {
                companyId = SecurityContext.getCurrentUser().getCompanyId();
            } else {
                companyId = userProvider.getUserById(userId).getCompanyId();
            }

            String weixinWorkInfo = weixinWorkService.getCompanyWeixinWorkInfo(companyId);
            if (weixinWorkInfo == null || weixinWorkInfo.isEmpty()) {
                log.error("未找到公司的企业微信配置信息, companyId: {}", companyId);
                return ApiResult.fail("未找到公司的企业微信配置信息");
            }

            JsonNode weixinWorkConfig = objectMapper.readTree(weixinWorkInfo);
            String corpId = weixinWorkConfig.path("corpId").asText();
            String corpSecret = weixinWorkConfig.path("corpSecret").asText();

            if (corpId == null || corpId.isEmpty() || corpSecret == null || corpSecret.isEmpty()) {
                log.error("企业微信配置信息不完整, corpId或corpSecret为空, companyId: {}", companyId);
                return ApiResult.fail("企业微信配置信息不完整");
            }

            return ApiResult.success(getGroupChatDetail(chatId, corpId, corpSecret));
        } catch (Exception e) {
            log.error("获取客户群组详情异常 chatId={}", chatId, e);
            return ApiResult.fail("获取客户群组详情异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取群组详情
     * @param chatId 群组ID
     * @param corpId 企业微信corpId
     * @param corpSecret 企业微信corpSecret
     * @return 群组详情，包含chat_id和群组名称
     */
    private Map<String, Object> getGroupChatDetail(String chatId, String corpId, String corpSecret) {
        try {
            String detailUrl = weixinWorkConfig.getApiBaseUrl() + "/externalcontact/groupchat/get?access_token=" + getAccessToken(corpId, corpSecret);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("need_name", 1);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = HttpUtils.doPost(detailUrl, jsonBody, null);
            
            JsonNode root = objectMapper.readTree(responseBody);
            int errcode = root.path("errcode").asInt();
            
            Map<String, Object> result = new HashMap<>();
            result.put("errcode", errcode);
            result.put("errmsg", root.path("errmsg").asText());

            if (errcode == 0) {
                result.put("group_chat", objectMapper.convertValue(root.path("group_chat"), Map.class));
                return result;
            } else {
                String errmsg = root.path("errmsg").asText();
                log.warn("获取群组详情失败 chatId={}: {} - {}", chatId, errcode, errmsg);
                return result;
            }
        } catch (Exception e) {
            log.error("获取群组详情异常 chatId={}", chatId, e);

            Map<String, Object> result = new HashMap<>();
            result.put("errcode", -1);
            result.put("errmsg", "获取群组详情异常: " + e.getMessage());
            return result;
        }
    }

    @RequestMapping(value = "/queryOrg", method = RequestMethod.GET)
    public ApiResult<?> queryOrg(@RequestParam(value = "parentOrgId", required = true) String parentOrgId) {
        try {
            String corpId = null;
            String corpSecret = null;
            
            if (parentOrgId == null || parentOrgId.isEmpty()) {
                return ApiResult.fail("必须提供组织ID(parentOrgId)");
            }
            
            // 获取指定组织的企业微信配置
            String weixinWorkInfo = weixinWorkService.getCompanyWeixinWorkInfo(parentOrgId);
            if (weixinWorkInfo != null && !weixinWorkInfo.isEmpty()) {
                try {
                    JsonNode configNode = objectMapper.readTree(weixinWorkInfo);
                    corpId = configNode.path("corpId").asText(null);
                    corpSecret = configNode.path("corpSecret").asText(null);
                } catch (Exception e) {
                    log.error("解析企业微信配置信息失败", e);
                    return ApiResult.fail("企业微信配置信息格式错误: " + e.getMessage());
                }
            }
            
            if (corpId == null || corpSecret == null) {
                log.error("未找到有效的企业微信配置信息");
                return ApiResult.fail("未找到有效的企业微信配置信息，请先配置企业微信信息");
            }
            
            String accessToken = getAccessToken(corpId, corpSecret);
            String url = weixinWorkConfig.getApiBaseUrl() + "/department/list?access_token=" + accessToken;
            
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
            String url = weixinWorkConfig.getApiBaseUrl() + "/user/list?access_token=" +  getAccessToken(null,null) +
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

    private String getAccessToken(String corpId, String corpSecret) {
        long currentTime = System.currentTimeMillis();
        if (cachedAccessToken != null && currentTime < tokenExpireTime) {
            log.debug("使用缓存的访问令牌，剩余有效时间: {} 秒", (tokenExpireTime - currentTime) / 1000);
            return cachedAccessToken;
        }
        synchronized (this) {
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
                return cachedAccessToken;
            }

            try {
                String url = weixinWorkConfig.getApiBaseUrl() + "/gettoken?corpid=" + corpId + "&corpsecret=" + corpSecret;
                
                log.info("正在获取企业微信访问令牌...");
                String responseBody = HttpUtils.doGet(url, null);

                JsonNode root = objectMapper.readTree(responseBody);
                int errcode = root.path("errcode").asInt();

                if (errcode == 0) {
                    String accessToken = root.path("access_token").asText();
                    int expiresIn = root.path("expires_in").asInt(7200);
                    cachedAccessToken = accessToken;
                    tokenExpireTime = System.currentTimeMillis() + (expiresIn - 200) * 1000L;
                    
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

    
    private String getUserIdByMobile(String mobile, String corpId, String corpSecret) {
        try {
            String accessToken = getAccessToken(corpId, corpSecret);
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
                return null;
            }
        } catch (Exception e) {
            log.error("根据手机号获取企业微信用户ID异常", e);
            return null;
        }
    }

}
