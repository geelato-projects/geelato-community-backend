package cn.geelato.web.platform.srv.company;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Company;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.PageInfo;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@ApiRestController("/company")
public class CompanyController extends BaseController {

    @Autowired
    private BaseMapper<Company> companyMapper;

    /**
     * 分页查询公司列表
     * 
     * @return 公司列表
     */
    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<List<Company>> pageQuery() {
        startPage();
        List<Company> list = companyMapper.selectList(null);
        PageInfo<Company> pageInfo = new PageInfo<>(list);
        return ApiPagedResult.success(
                list,
                getPageNum(),
                getPageSize(),
                list.size(),
                pageInfo.getTotal()
        );
    }
    
    /**
     * 保存公司信息
     * 根据是否有ID决定新增或更新
     * 
     * @param company 公司信息
     * @return 操作结果
     */
    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<String> save(@RequestBody Company company) {
        try {
            if (company.getId() != null && !company.getId().isEmpty()) {
                Company existingCompany = companyMapper.selectById(company.getId());
                
                if (existingCompany != null) {
                    companyMapper.updateById(company);
                    log.info("更新公司信息成功, ID: {}, 名称: {}", company.getId(), company.getName());
                    return ApiResult.success("更新公司信息成功");
                } else {
                    companyMapper.insert(company);
                    log.info("新增公司信息成功, ID: {}, 名称: {}", company.getId(), company.getName());
                    return ApiResult.success("新增公司信息成功");
                }
            } else {
                companyMapper.insert(company);
                log.info("新增公司信息成功, 名称: {}", company.getName());
                return ApiResult.success("新增公司信息成功");
            }
        } catch (Exception e) {
            log.error("保存公司信息失败: {}", e.getMessage(), e);
            return ApiResult.fail("保存公司信息失败：" + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取公司信息
     * 
     * @param id 公司ID
     * @return 公司信息
     */
    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<Company> get(@NotNull(message = "公司ID不能为空") @PathVariable String id) {
        try {
            Company company = companyMapper.selectById(id);
            if (company != null) {
                return ApiResult.success(company);
            } else {
                return ApiResult.fail("未找到对应的公司信息");
            }
        } catch (Exception e) {
            log.error("获取公司信息失败", e);
            return ApiResult.fail("获取公司信息失败：" + e.getMessage());
        }
    }
    
    /**
     * 根据ID删除公司信息
     * 支持 DELETE 和 GET 请求
     * 
     * @param id 公司ID
     * @return 操作结果
     */
    @RequestMapping(value = "/delete/{id}", method = {RequestMethod.DELETE, RequestMethod.GET})
    public ApiResult<String> delete(@NotNull(message = "公司ID不能为空") @PathVariable String id) {
        try {
            int result = companyMapper.deleteById(id);
            if (result > 0) {
                return ApiResult.success("删除公司信息成功");
            } else {
                return ApiResult.fail("删除公司信息失败，可能该公司不存在");
            }
        } catch (Exception e) {
            log.error("删除公司信息失败", e);
            return ApiResult.fail("删除公司信息失败：" + e.getMessage());
        }
    }
}