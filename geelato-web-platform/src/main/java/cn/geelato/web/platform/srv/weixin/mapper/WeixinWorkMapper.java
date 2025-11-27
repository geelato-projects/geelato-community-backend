package cn.geelato.web.platform.srv.weixin.mapper;

import cn.geelato.meta.User;
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

    @Select("SELECT weixin_work_info FROM platform_company WHERE org_id = #{orgId}")
    String getCompanyWeixinWorkInfo(@Param("orgId") String orgId);

    @Update("UPDATE platform_user SET weixin_work_userId = #{weixinWorkUserId} WHERE id = #{userId}")
    void updateUserWeixinWorkUserId(@Param("weixinWorkUserId") String weixinWorkUserId, @Param("userId") String userId);

    @Select("WITH RECURSIVE org_tree AS (\n" +
            "  SELECT id FROM platform_org WHERE id = #{orgId}\n" +
            "  UNION ALL\n" +
            "  SELECT o.id FROM platform_org o JOIN org_tree ot ON o.pid = ot.id\n" +
            ")\n" +
            "SELECT u.* FROM platform_user u WHERE u.org_id IN (SELECT id FROM org_tree)")
    List<User> findUsersByOrgId(@Param("orgId") String orgId);

    @Select("SELECT weixin_work_userId FROM platform_user WHERE id = #{userId}")
    String getWeixinWorkUserIdByUserId(@Param("userId") String userId);
}
