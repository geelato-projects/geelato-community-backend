package cn.geelato.web.platform.m.tenant.rest;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.tenant.service.TenantService;
import cn.geelato.web.platform.m.tenant.entity.Tenant;
import cn.geelato.web.platform.m.tenant.mapper.TenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController("/tenant")
@Slf4j
public class TenantController extends BaseController {
    private static final Class<Tenant> CLAZZ = Tenant.class;
    private final TenantService tenantService;
    private final TenantMapper tenantMapper;

    @Autowired
    public TenantController(TenantService tenantService, TenantMapper tenantMapper) {
        this.tenantService = tenantService;
        this.tenantMapper = tenantMapper;
    }

    /**
     * 查询租户列表
     */
    @GetMapping("/list")
    public ApiResult<List<Tenant>> queryTenantList(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String corpId) {
        try {
            QueryWrapper<Tenant> queryWrapper = new QueryWrapper<>();
            if (StringUtils.hasText(code)) {
                queryWrapper.like("code", code);
            }
            if (StringUtils.hasText(companyName)) {
                queryWrapper.like("company_name", companyName);
            }
            if (StringUtils.hasText(corpId)) {
                queryWrapper.eq("corp_id", corpId);
            }
            queryWrapper.eq("del_status", 0);
            queryWrapper.orderByDesc("create_at");
            List<Tenant> tenants = tenantMapper.selectList(queryWrapper);
            return ApiResult.success(tenants);
        } catch (Exception e) {
            log.error("查询租户列表失败", e);
            return ApiResult.fail("查询租户列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询租户信息
     */
    @GetMapping("/{id}")
    public ApiResult<Tenant> queryTenantById(
            @PathVariable String id) {
        try {
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("租户ID不能为空");
            }
            Tenant tenant = tenantMapper.selectById(id);
            if (tenant == null) {
                return ApiResult.fail("租户不存在");
            }
            return ApiResult.success(tenant);
        } catch (Exception e) {
            log.error("查询租户信息失败", e);
            return ApiResult.fail("查询租户信息失败: " + e.getMessage());
        }
    }

    /**
     * 根据租户编码查询租户信息
     */
    @GetMapping("/code/{code}")
    public ApiResult<Tenant> queryTenantByCode(
            @PathVariable String code) {
        try {
            if (!StringUtils.hasText(code)) {
                return ApiResult.fail("租户编码不能为空");
            }
            Tenant tenant = tenantMapper.selectByCode(code);
            if (tenant == null) {
                return ApiResult.fail("租户不存在");
            }
            return ApiResult.success(tenant);
        } catch (Exception e) {
            log.error("查询租户信息失败", e);
            return ApiResult.fail("查询租户信息失败: " + e.getMessage());
        }
    }



    /**
     * 初始化租户（通过租户编码）
     */
    @GetMapping("/initialize/{code}")
    @Transactional
    public ApiResult<?> initializeTenant(
            @PathVariable String code) {
        try {
            if (!StringUtils.hasText(code)) {
                return ApiResult.fail("租户编码不能为空");
            }

            // 查找租户
            Tenant tenant = tenantMapper.selectByCode(code);
            if (tenant == null) {
                return ApiResult.fail("租户不存在");
            }

            // 检查租户是否已被删除
            if (tenant.getDelStatus() == 1) {
                return ApiResult.fail("租户已被删除，无法初始化");
            }

            // 执行租户初始化逻辑，创建租户站点、组织、用户、角色等
            Map<String, String> initResult = tenantService.afterCreate(tenant);

            // 更新租户状态或其他初始化标记
            tenant.setUpdateAt(new java.util.Date());

            int result = tenantMapper.updateById(tenant);

            if (result > 0) {
                // 直接返回包含租户信息和初始化结果（用户名和密码）的Map
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("tenant", tenant);
                responseData.put("userName", initResult.get("userName"));
                responseData.put("password", initResult.get("password"));
                return ApiResult.success(responseData, "租户初始化成功");
            } else {
                return ApiResult.fail("租户初始化失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("租户初始化参数错误: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("租户初始化失败", e);
            return ApiResult.fail("租户初始化失败: " + e.getMessage());
        }
    }

    /**
     * 更新租户信息
     */
    @PutMapping("/{id}")
    @Transactional
    public ApiResult<Boolean> updateTenant(
            @PathVariable String id,
            @RequestBody Tenant tenant,
            HttpServletRequest request) {
        try {
            tenant.setId(id);

            // 从请求中获取更新者信息
            String updater = request.getHeader("userId");
            String updaterName = request.getHeader("userName");

            if (updater == null) {
                updater = "system";
            }
            if (updaterName == null) {
                updaterName = "系统管理员";
            }

            // 参数验证
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("租户ID不能为空");
            }

            // 检查租户是否存在
            Tenant existingTenant = tenantMapper.selectById(id);
            if (existingTenant == null) {
                return ApiResult.fail("租户不存在");
            }

            // 设置更新信息
            tenant.setUpdateAt(new java.util.Date());
            tenant.setUpdater(updater);
            tenant.setUpdaterName(updaterName);

            // 更新租户
            int result = tenantMapper.updateById(tenant);
            return result > 0 ? ApiResult.success(true, "更新成功") : ApiResult.fail("更新失败");
        } catch (IllegalArgumentException e) {
            log.warn("更新租户参数错误: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新租户失败", e);
            return ApiResult.fail("更新租户失败: " + e.getMessage());
        }
    }
    @RequestMapping(value = "/reset/password/{id}", method = RequestMethod.GET)
    public ApiResult<Map<String, String>> resetPassword(@PathVariable() String id) {
        try {
            Tenant source = tenantService.getModel(CLAZZ, id);
            return ApiResult.success(tenantService.resetPassword(source));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}