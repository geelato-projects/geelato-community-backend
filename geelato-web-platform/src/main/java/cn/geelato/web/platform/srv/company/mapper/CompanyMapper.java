package cn.geelato.web.platform.srv.company.mapper;

import cn.geelato.meta.Company;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 公司信息Mapper接口
 */
@Mapper
public interface CompanyMapper {

    /**
     * 查询公司列表
     * @param params 查询参数
     * @return 公司列表
     */
    List<Company> selectCompanyList(@Param("params") Map<String, Object> params);

    /**
     * 分页查询公司列表
     * @param page 分页对象
     * @param params 查询参数
     * @return 分页结果
     */
    Page<Company> selectCompany(Page<Company> page, @Param("params") Map<String, Object> params);

    /**
     * 根据ID查询公司信息
     * @param id 公司ID
     * @return 公司信息
     */
    Company selectCompanyById(@Param("id") String id);

    /**
     * 查询公司总数
     * @param params 查询参数
     * @return 总数
     */
    int selectCompanyCount(@Param("params") Map<String, Object> params);
}