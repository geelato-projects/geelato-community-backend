package cn.geelato.web.platform.mapper;

import cn.geelato.meta.Tenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租户管理Mapper接口
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /**
     * 分页查询租户列表
     * @param page 分页对象
     * @param code 租户编码
     * @param companyName 企业名称
     * @param corpId 企业ID
     * @return 分页结果
     */
    Page<Tenant> selectTenantList(Page<Tenant> page,
                                          @Param("code") String code,
                                          @Param("companyName") String companyName,
                                          @Param("corpId") String corpId);

    /**
     * 根据租户编码查询租户信息
     * @param code 租户编码
     * @return 租户信息
     */
    Tenant selectByCode(@Param("code") String code);

    /**
     * 根据企业名称查询租户信息
     * @param companyName 企业名称
     * @return 租户信息
     */
    Tenant selectByCompanyName(@Param("companyName") String companyName);

    /**
     * 根据企业ID查询租户信息
     * @param corpId 企业ID
     * @return 租户信息
     */
    Tenant selectByCorpId(@Param("corpId") String corpId);

    /**
     * 查询所有有效租户
     * @return 租户列表
     */
    List<Tenant> selectAllValidTenants();
}