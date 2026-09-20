package cn.geelato.web.common.oauth2;

import cn.geelato.utils.LocalBoundedCache;

/**
 * Token管理器，用于存储和管理access_token与refresh_token的映射关系
 */
public class TokenManager {

    /**
     * 映射须与 token 同寿，不设 TTL，仅以容量护栏消除无限增长
     */
    private static final LocalBoundedCache<String, String> tokenMap = new LocalBoundedCache<>("oauth-token", 0L, 100_000);
    
    /**
     * 存储token映射关系
     * @param accessToken 访问令牌
     * @param refreshToken 刷新令牌
     */
    public static void storeTokens(String accessToken, String refreshToken) {
        if (accessToken != null && refreshToken != null) {
            tokenMap.put(accessToken, refreshToken);
        }
    }
    
    /**
     * 根据access_token获取对应的refresh_token
     * @param accessToken 访问令牌
     * @return 对应的刷新令牌，如果不存在则返回null
     */
    public static String getRefreshToken(String accessToken) {
        return tokenMap.get(accessToken);
    }
    
    /**
     * 移除token映射关系
     * @param accessToken 访问令牌
     */
    public static void removeTokens(String accessToken) {
        tokenMap.remove(accessToken);
    }
    
    /**
     * 更新token映射关系（当刷新token后）
     * @param oldAccessToken 旧的访问令牌
     * @param newAccessToken 新的访问令牌
     * @param newRefreshToken 新的刷新令牌
     */
    public static void updateTokens(String oldAccessToken, String newAccessToken, String newRefreshToken) {
        // 移除旧的映射
        removeTokens(oldAccessToken);
        // 添加新的映射
        storeTokens(newAccessToken, newRefreshToken);
    }
    
    /**
     * 清空所有token映射
     */
    public static void clearAll() {
        tokenMap.clear();
    }
}