package cn.geelato.web.platform.srv.company;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.Company;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
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
@DesignTimeApiRestController("/company")
public class CompanyController extends BaseController {

    @Autowired
    private BaseMapper<Company> companyMapper;

    /**
     * 鍒嗛〉鏌ヨ鍏徃鍒楄〃
     * 
     * @return 鍏徃鍒楄〃
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
     * 淇濆瓨鍏徃淇℃伅
     * 鏍规嵁鏄惁鏈塈D鍐冲畾鏂板鎴栨洿鏂?     * 
     * @param company 鍏徃淇℃伅
     * @return 鎿嶄綔缁撴灉
     */
    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<String> save(@RequestBody Company company) {
        try {
            if (company.getId() != null && !company.getId().isEmpty()) {
                Company existingCompany = companyMapper.selectById(company.getId());
                
                if (existingCompany != null) {
                    companyMapper.updateById(company);
                    log.info("鏇存柊鍏徃淇℃伅鎴愬姛, ID: {}, 鍚嶇О: {}", company.getId(), company.getName());
                    return ApiResult.success("鏇存柊鍏徃淇℃伅鎴愬姛");
                } else {
                    companyMapper.insert(company);
                    log.info("鏂板鍏徃淇℃伅鎴愬姛, ID: {}, 鍚嶇О: {}", company.getId(), company.getName());
                    return ApiResult.success("鏂板鍏徃淇℃伅鎴愬姛");
                }
            } else {
                companyMapper.insert(company);
                log.info("鏂板鍏徃淇℃伅鎴愬姛, 鍚嶇О: {}", company.getName());
                return ApiResult.success("鏂板鍏徃淇℃伅鎴愬姛");
            }
        } catch (Exception e) {
            log.error("淇濆瓨鍏徃淇℃伅澶辫触: {}", e.getMessage(), e);
            return ApiResult.fail("保存公司信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 鏍规嵁ID鑾峰彇鍏徃淇℃伅
     * 
     * @param id 鍏徃ID
     * @return 鍏徃淇℃伅
     */
    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<Company> get(@NotNull(message = "鍏徃ID涓嶈兘涓虹┖") @PathVariable String id) {
        try {
            Company company = companyMapper.selectById(id);
            if (company != null) {
                return ApiResult.success(company);
            } else {
                return ApiResult.fail("鏈壘鍒板搴旂殑鍏徃淇℃伅");
            }
        } catch (Exception e) {
            log.error("鑾峰彇鍏徃淇℃伅澶辫触", e);
            return ApiResult.fail("获取公司信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 鏍规嵁ID鍒犻櫎鍏徃淇℃伅
     * 鏀寔 DELETE 鍜?GET 璇锋眰
     * 
     * @param id 鍏徃ID
     * @return 鎿嶄綔缁撴灉
     */
    @RequestMapping(value = "/delete/{id}", method = {RequestMethod.DELETE, RequestMethod.GET})
    public ApiResult<String> delete(@NotNull(message = "鍏徃ID涓嶈兘涓虹┖") @PathVariable String id) {
        try {
            int result = companyMapper.deleteById(id);
            if (result > 0) {
                return ApiResult.success("鍒犻櫎鍏徃淇℃伅鎴愬姛");
            } else {
                return ApiResult.fail("删除公司信息失败，可能该公司不存在");
            }
        } catch (Exception e) {
            log.error("鍒犻櫎鍏徃淇℃伅澶辫触", e);
            return ApiResult.fail("删除公司信息失败: " + e.getMessage());
        }
    }
}
