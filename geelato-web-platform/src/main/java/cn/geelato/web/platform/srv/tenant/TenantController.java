package cn.geelato.web.platform.srv.tenant;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.Tenant;
import cn.geelato.web.platform.srv.tenant.service.TenantOrmService;
import cn.geelato.web.platform.srv.tenant.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DesignTimeApiRestController("/tenant")
@Slf4j
public class TenantController extends BaseController {
    private static final Class<Tenant> CLAZZ = Tenant.class;
    private final TenantService tenantService;
    private final TenantOrmService tenantOrmService;

    @Autowired
    public TenantController(TenantService tenantService, TenantOrmService tenantOrmService) {
        this.tenantService = tenantService;
        this.tenantOrmService = tenantOrmService;
    }

    /**
     * 鏌ヨ绉熸埛鍒楄〃
     */
    @GetMapping("/list")
    public ApiResult<List<Tenant>> queryTenantList(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String corpId) {
        try {
            List<Tenant> tenants = tenantOrmService.queryTenantList(code, companyName, corpId);
            return ApiResult.success(tenants);
        } catch (Exception e) {
            log.error("鏌ヨ绉熸埛鍒楄〃澶辫触", e);
            return ApiResult.fail("鏌ヨ绉熸埛鍒楄〃澶辫触: " + e.getMessage());
        }
    }

    /**
     * 鏍规嵁ID鏌ヨ绉熸埛淇℃伅
     */
    @GetMapping("/{id}")
    public ApiResult<Tenant> queryTenantById(
            @PathVariable String id) {
        try {
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("绉熸埛ID涓嶈兘涓虹┖");
            }
            Tenant tenant = tenantOrmService.getById(id);
            if (tenant == null) {
                return ApiResult.fail("租户不存在");
            }
            return ApiResult.success(tenant);
        } catch (Exception e) {
            log.error("鏌ヨ绉熸埛淇℃伅澶辫触", e);
            return ApiResult.fail("鏌ヨ绉熸埛淇℃伅澶辫触: " + e.getMessage());
        }
    }

    /**
     * 鏍规嵁绉熸埛缂栫爜鏌ヨ绉熸埛淇℃伅
     */
    @GetMapping("/code/{code}")
    public ApiResult<Tenant> queryTenantByCode(
            @PathVariable String code) {
        try {
            if (!StringUtils.hasText(code)) {
                return ApiResult.fail("绉熸埛缂栫爜涓嶈兘涓虹┖");
            }
            Tenant tenant = tenantOrmService.getByCode(code);
            if (tenant == null) {
                return ApiResult.fail("租户不存在");
            }
            return ApiResult.success(tenant);
        } catch (Exception e) {
            log.error("鏌ヨ绉熸埛淇℃伅澶辫触", e);
            return ApiResult.fail("鏌ヨ绉熸埛淇℃伅澶辫触: " + e.getMessage());
        }
    }


    /**
     * 鍒濆鍖栫鎴凤紙閫氳繃绉熸埛缂栫爜锛?
     */
    @GetMapping("/initialize/{code}")
    @Transactional
    public ApiResult<?> initializeTenant(
            @PathVariable String code) {
        try {
            if (!StringUtils.hasText(code)) {
                return ApiResult.fail("绉熸埛缂栫爜涓嶈兘涓虹┖");
            }

            // 鏌ユ壘绉熸埛
            Tenant tenant = tenantOrmService.getByCode(code);
            if (tenant == null) {
                return ApiResult.fail("租户不存在");
            }

            // 妫€鏌ョ鎴锋槸鍚﹀凡琚垹闄?
            if (tenant.getDelStatus() == 1) {
                return ApiResult.fail("绉熸埛宸茶鍒犻櫎锛屾棤娉曞垵濮嬪寲");
            }

            // 鎵ц绉熸埛鍒濆鍖栭€昏緫锛屽垱寤虹鎴风珯鐐广€佺粍缁囥€佺敤鎴枫€佽鑹茬瓑
            Map<String, String> initResult = tenantService.afterCreate(tenant);

            tenantOrmService.touchById(tenant.getId());
            tenant = tenantOrmService.getById(tenant.getId());
            if (tenant != null) {
                // 鐩存帴杩斿洖鍖呭惈绉熸埛淇℃伅鍜屽垵濮嬪寲缁撴灉锛堢敤鎴峰悕鍜屽瘑鐮侊級鐨凪ap
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("tenant", tenant);
                responseData.put("userName", initResult.get("userName"));
                responseData.put("password", initResult.get("password"));
                return ApiResult.success(responseData, "租户初始化成功");
            } else {
                return ApiResult.fail("租户初始化失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("绉熸埛鍒濆鍖栧弬鏁伴敊璇? {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("租户初始化失败", e);
            return ApiResult.fail("租户初始化失败: " + e.getMessage());
        }
    }

    /**
     * 鏇存柊绉熸埛淇℃伅
     */
    @PutMapping("/{id}")
    @Transactional
    public ApiResult<Boolean> updateTenant(
            @PathVariable String id,
            @RequestBody Tenant tenant) {
        try {
            tenant.setId(id);

            // 浠嶴ecurityContext鑾峰彇鏇存柊鑰呬俊鎭?
            String updater = SecurityContext.getCurrentUser().getUserId();
            String updaterName = SecurityContext.getCurrentUser().getUserName();

            if (updater == null) {
                updater = "system";
            }
            if (updaterName == null) {
                updaterName = "系统管理员";
            }

            // 鍙傛暟楠岃瘉
            if (!StringUtils.hasText(id)) {
                return ApiResult.fail("绉熸埛ID涓嶈兘涓虹┖");
            }

            // 妫€鏌ョ鎴锋槸鍚﹀瓨鍦?
            Tenant existingTenant = tenantOrmService.getById(id);
            if (existingTenant == null) {
                return ApiResult.fail("租户不存在");
            }

            // 璁剧疆鏇存柊淇℃伅
            tenant.setUpdateAt(new java.util.Date());
            tenant.setUpdater(updater);
            tenant.setUpdaterName(updaterName);

            // 鏇存柊绉熸埛
            tenantOrmService.updateById(tenant);
            return ApiResult.success(true, "鏇存柊鎴愬姛");
        } catch (IllegalArgumentException e) {
            log.warn("鏇存柊绉熸埛鍙傛暟閿欒: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("鏇存柊绉熸埛澶辫触", e);
            return ApiResult.fail("鏇存柊绉熸埛澶辫触: " + e.getMessage());
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
     * 鍒涘缓绉熸埛
     */
    @PostMapping("/create")
    @Transactional
    public ApiResult<?> createTenant(@RequestBody Tenant tenant) {
        try {
            // 鍙傛暟楠岃瘉
            if (!StringUtils.hasText(tenant.getCompanyName())) {
                return ApiResult.fail("鍏徃鍚嶇О涓嶈兘涓虹┖");
            }
            if (!StringUtils.hasText(tenant.getCode())) {
                return ApiResult.fail("绉熸埛缂栫爜涓嶈兘涓虹┖");
            }

            // 妫€鏌ョ鎴风紪鐮佹槸鍚﹀凡瀛樺湪
            Tenant existingTenant = tenantOrmService.getByCode(tenant.getCode());
            if (existingTenant != null) {
                return ApiResult.fail("租户编码已存在");
            }

            // 浠嶴ecurityContext鑾峰彇鍒涘缓鑰呬俊鎭?
            String creator = SecurityContext.getCurrentUser().getUserId();
            String creatorName = SecurityContext.getCurrentUser().getUserName();

            if (creator == null) {
                creator = "system";
            }
            if (creatorName == null) {
                creatorName = "系统管理员";
            }

            // 璁剧疆鍒涘缓淇℃伅
            tenant.setCreator(creator);
            tenant.setCreatorName(creatorName);
            tenant.setCreateAt(new java.util.Date());
            tenant.setDelStatus(0);
            tenant.setDeleteAt(DateUtils.defaultDeleteAt());

            // 鍒涘缓绉熸埛
            tenantOrmService.create(tenant);

            // 鐩存帴杩斿洖绉熸埛淇℃伅
            return ApiResult.success(tenant, "绉熸埛鍒涘缓鎴愬姛");
        } catch (IllegalArgumentException e) {
            log.warn("鍒涘缓绉熸埛鍙傛暟閿欒: {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("鍒涘缓绉熸埛澶辫触", e);
            return ApiResult.fail("鍒涘缓绉熸埛澶辫触: " + e.getMessage());
        }
    }

    /**
     * 閭€璇风鎴凤紙浠呭垱寤哄寘鍚偖绠卞湴鍧€鐨勫緟濉鎴蜂俊鎭級
     */
    @PostMapping("/invite")
    @Transactional
    public ApiResult<?> inviteTenant(@RequestParam String email) {
        try {
            // 鍙傛暟楠岃瘉
            if (!StringUtils.hasText(email)) {
                return ApiResult.fail("閭鍦板潃涓嶈兘涓虹┖");
            }

            // 鐢熸垚绉熸埛缂栫爜锛堜娇鐢ㄩ偖绠卞墠缂€鍔犻殢鏈哄瓧绗︼級
            String emailPrefix = email.split("@")[0];
            String tenantCode = emailPrefix + "_" + System.currentTimeMillis() % 10000;

            // 妫€鏌ョ鎴风紪鐮佹槸鍚﹀凡瀛樺湪
            Tenant existingTenant = tenantOrmService.getByCode(tenantCode);
            if (existingTenant != null) {
                // 濡傛灉宸插瓨鍦紝娣诲姞闅忔満鍚庣紑
                tenantCode = tenantCode + "_" + (int) (Math.random() * 1000);
            }

            // 浠嶴ecurityContext鑾峰彇鍒涘缓鑰呬俊鎭?
            String creator = SecurityContext.getCurrentUser().getUserId();
            String creatorName = SecurityContext.getCurrentUser().getUserName();

            if (creator == null) {
                creator = "system";
            }
            if (creatorName == null) {
                creatorName = "系统管理员";
            }

            // 鍒涘缓绉熸埛瀵硅薄锛屽彧璁剧疆閭鍜屽繀瑕佷俊鎭紝鍏朵粬淇℃伅鐣欑┖
            Tenant tenant = new Tenant();
            tenant.setMainEmail(email);
            tenant.setCode(tenantCode);
            tenant.setCompanyName("待完善"); // 临时名称，等待用户完善
            tenant.setCreator(creator);
            tenant.setCreatorName(creatorName);
            tenant.setCreateAt(new java.util.Date());
            tenant.setDelStatus(0);

            // 鍒涘缓绉熸埛
            tenantOrmService.create(tenant);

            // TODO: 鍙戦€侀個璇烽偖浠剁粰鐢ㄦ埛锛屽寘鍚畬鍠勪俊鎭殑閾炬帴

            // 杩斿洖绉熸埛淇℃伅
            return ApiResult.success(tenant, "绉熸埛閭€璇锋垚鍔燂紝绛夊緟瀹屽杽淇℃伅");
        } catch (IllegalArgumentException e) {
            log.warn("閭€璇风鎴峰弬鏁伴敊璇? {}", e.getMessage());
            return ApiResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("邀请租户失败", e);
            return ApiResult.fail("邀请租户失败: " + e.getMessage());
        }
    }
}

