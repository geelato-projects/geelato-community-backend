package cn.geelato.web.platform.weixin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 微信配置相关数据库操作Mapper
 */
@Mapper
public interface WeixinConfigMapper {
    
    /**
     * 获取租户的微信配置
     * @param tenantId 租户ID
     * @return 微信配置JSON字符串
     */
    @Select("SELECT weixin_config FROM platform_tenant WHERE id = #{tenantId}")
    String getWeixinConfig(@Param("tenantId") String tenantId);
    
    /**
     * 更新租户的微信配置
     * @param tenantId 租户ID
     * @param weixinConfig 微信配置JSON字符串
     * @return 影响的行数
     */
    @Update("UPDATE platform_tenant SET weixin_config = #{weixinConfig} WHERE id = #{tenantId}")
    int updateWeixinConfig(@Param("tenantId") String tenantId, @Param("weixinConfig") String weixinConfig);
}