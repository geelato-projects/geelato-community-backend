package cn.geelato.web.platform.m.tenant.rest;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.tenant.entity.Tenant;
import cn.geelato.web.platform.m.tenant.mapper.TenantMapper;
import cn.geelato.web.platform.m.tenant.service.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody Tenant tenant) {
        try {
            tenant.setId(id);

            // 从SecurityContext获取更新者信息
            String updater = SecurityContext.getCurrentUser().getUserId();
            String updaterName = SecurityContext.getCurrentUser().getUserName();

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

    /**
     * 创建租户
     */
    @PostMapping("/create")
    @Transactional
    public ApiResult<?> createTenant(@RequestBody Tenant tenant) {
        try {
            // 参数验证
            if (!StringUtils.hasText(tenant.getCompanyName())) {
                return ApiResult.fail("公司名称不能为空");
            }
            if (!StringUtils.hasText(tenant.getCode())) {
                return ApiResult.fail("租户编码不能为空");
            }

            // 检查租户编码是否已存在
            Tenant existingTenant = tenantMapper.selectByCode(tenant.getCode());
            if (existingTenant != null) {
                return ApiResult.fail("租户编码已存在");
            }

            // 从SecurityContext获取创建者信息
            String creator = SecurityContext.getCurrentUser().getUserId();
            String creatorName = SecurityContext.getCurrentUser().getUserName();

            if (creator == null) {
                creator = "system";
            }
            if (creatorName == null) {
                creatorName = "系统管理员";
            }

            // 设置创建信息
            tenant.setCreator(creator);
            tenant.setCreatorName(creatorName);
            tenant.setCreateAt(new java.util.Date());
            tenant.setDelStatus(0);
            tenant.setDeleteAt(DateUtils.defaultDeleteAt());

            // 创建租户
            int result = tenantMapper.insert(tenant);
            if (result <= 0) {
                return ApiResult.fail("创建租户失败");
            }

            // 直接返回租户信息
            return ApiResult.success(tenant, "租户创建成功");
        } catch (IllegalArgumentException e) {
            log.warn("创建租户参数错误: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建租户失败", e);
            return ApiResult.fail("创建租户失败: " + e.getMessage());
        }
    }

    /**
     * 邀请租户（仅创建包含邮箱地址的待填租户信息）
     */
    @PostMapping("/invite")
    @Transactional
    public ApiResult<?> inviteTenant(@RequestParam String email) {
        try {
            // 参数验证
            if (!StringUtils.hasText(email)) {
                return ApiResult.fail("邮箱地址不能为空");
            }

            // 生成租户编码（使用邮箱前缀加随机字符）
            String emailPrefix = email.split("@")[0];
            String tenantCode = emailPrefix + "_" + System.currentTimeMillis() % 10000;

            // 检查租户编码是否已存在
            Tenant existingTenant = tenantMapper.selectByCode(tenantCode);
            if (existingTenant != null) {
                // 如果已存在，添加随机后缀
                tenantCode = tenantCode + "_" + (int) (Math.random() * 1000);
            }

            // 从SecurityContext获取创建者信息
            String creator = SecurityContext.getCurrentUser().getUserId();
            String creatorName = SecurityContext.getCurrentUser().getUserName();

            if (creator == null) {
                creator = "system";
            }
            if (creatorName == null) {
                creatorName = "系统管理员";
            }

            // 创建租户对象，只设置邮箱和必要信息，其他信息留空
            Tenant tenant = new Tenant();
            tenant.setMainEmail(email);
            tenant.setCode(tenantCode);
            tenant.setCompanyName("待完善"); // 临时名称，等待用户完善
            tenant.setCreator(creator);
            tenant.setCreatorName(creatorName);
            tenant.setCreateAt(new java.util.Date());
            tenant.setDelStatus(0);

            // 创建租户
            int result = tenantMapper.insert(tenant);
            if (result <= 0) {
                return ApiResult.fail("邀请租户失败");
            }

            // TODO: 发送邀请邮件给用户，包含完善信息的链接

            // 返回租户信息
            return ApiResult.success(tenant, "租户邀请成功，等待完善信息");
        } catch (IllegalArgumentException e) {
            log.warn("邀请租户参数错误: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("邀请租户失败", e);
            return ApiResult.fail("邀请租户失败: " + e.getMessage());
        }
    }
}
