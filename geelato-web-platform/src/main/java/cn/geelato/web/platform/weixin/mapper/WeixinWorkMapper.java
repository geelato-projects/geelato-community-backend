package cn.geelato.web.platform.weixin.mapper;

import cn.geelato.web.common.security.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 企业微信相关数据库操作Mapper
 */
@Mapper
public interface WeixinWorkMapper {
    
    /**
     * 更新用户的企业微信userId
     *
     * @param weixinWorkUserId 企业微信用户ID
     * @param userId           平台用户ID
     */
    @Update("UPDATE platform_user SET weixin_work_userId = #{weixinWorkUserId} WHERE id = #{userId}")
    void updateUserWeixinWorkUserId(@Param("weixinWorkUserId") String weixinWorkUserId, @Param("userId") String userId);
    
    /**
     * 根据组织ID查询用户
     * @param orgId 组织ID
     * @return 用户列表
     */
    @Select("SELECT * FROM platform_user WHERE org_id = #{orgId}")
    List<User> findUsersByOrgId(@Param("orgId") String orgId);
    
    /**
     * 查询所有用户
     * @return 所有用户列表
     */
    @Select("SELECT * FROM platform_user")
    List<User> findAllUsers();
    
    /**
     * 根据用户ID获取企业微信用户ID
     * @param userId 平台用户ID
     * @return 企业微信用户ID
     */
    @Select("SELECT weixin_work_userId FROM platform_user WHERE id = #{userId}")
    String getWeixinWorkUserIdByUserId(@Param("userId") String userId);
}