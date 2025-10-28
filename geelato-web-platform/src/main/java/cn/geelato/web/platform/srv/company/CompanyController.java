package cn.geelato.web.platform.srv.company;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.meta.Company;
import cn.geelato.orm.PageResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.company.mapper.CompanyMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公司信息控制器
 * 
 * @author geelato
 */
@Slf4j
@ApiRestController("/company")
public class CompanyController extends BaseController {
    
    private static final Class<Company> CLAZZ = Company.class;
    
    @Autowired
    private CompanyMapper companyMapper;

    /**
     * 分页查询公司列表
     * 
     * @return 公司列表
     */
    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult<List<Company>> pageQuery() {
        try {
            // 1. 获取请求体和分页参数
            Map<String, Object> requestBody = this.getRequestBody();
            PageQueryRequest pageQueryRequest = this.getPageQueryParameters(requestBody);
            
            // 2. 使用封装方法构建查询参数
            Map<String, Object> params = buildQueryParams(CLAZZ, requestBody, true);

            // 3. 启动PageHelper分页，不侵入性传递分页参数
            PageHelper.startPage(pageQueryRequest.getPageNum(), pageQueryRequest.getPageSize());

            // 4. 执行查询，Mapper返回列表
            List<Company> list = companyMapper.selectCompanyList(params);

            // 5. 通过PageInfo获取总数等分页信息
            PageInfo<Company> pageInfo = new PageInfo<>(list);

            // 6. 直接构建ApiPagedResult
            return ApiPagedResult.success(
                    list,
                    pageQueryRequest.getPageNum(),
                    pageQueryRequest.getPageSize(),
                    list.size(),
                    pageInfo.getTotal()
            );
            
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }
    
    /**
     * 将FilterGroup转换为Map参数
     * @param filterGroup 过滤条件组
     * @return 查询参数Map
     */
    // 已由BaseController提供封装方法，移除本地转换逻辑


    

}