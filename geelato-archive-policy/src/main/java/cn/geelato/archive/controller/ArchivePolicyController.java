package cn.geelato.archive.controller;

import cn.geelato.archive.entity.ArchivePolicy;
import cn.geelato.archive.exception.ArchiveException;
import cn.geelato.archive.service.ArchivePolicyService;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * 归档策略管理接口（模块自包含，不依赖 web-platform 的 BaseController，
 * 轻量自持：手动解析分页与等值过滤参数）。
 */
@ApiRestController("/archive/policy")
@Slf4j
public class ArchivePolicyController {

    private static final String DEFAULT_ORDER_BY = "seq_no asc";

    private final ArchivePolicyService policyService;

    @Autowired
    public ArchivePolicyController(ArchivePolicyService policyService) {
        this.policyService = policyService;
    }

    @RequestMapping(value = "/pageQuery", method = RequestMethod.POST)
    public ApiPagedResult pageQuery(@RequestBody(required = false) Map<String, Object> body) {
        try {
            PageQueryRequest request = buildPageQueryRequest(body);
            FilterGroup filterGroup = buildFilterGroup(body, "tableName", "policyType", "enableStatus", "code", "connectId");
            return policyService.pageQuery(filterGroup, request);
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiPagedResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    public ApiResult<ArchivePolicy> get(@PathVariable String id) {
        try {
            ArchivePolicy policy = policyService.get(id);
            return ApiResult.success(policy);
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/createOrUpdate", method = RequestMethod.POST)
    public ApiResult<ArchivePolicy> createOrUpdate(@RequestBody ArchivePolicy form) {
        try {
            return ApiResult.success(policyService.createOrUpdate(form));
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/isDelete/{id}", method = RequestMethod.DELETE)
    public ApiResult<Boolean> isDelete(@PathVariable String id) {
        try {
            policyService.isDelete(id);
            return ApiResult.success(Boolean.TRUE);
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 启用前校验（只校验不启用，返回通过/具体拒绝原因） */
    @RequestMapping(value = "/validate/{id}", method = RequestMethod.POST)
    public ApiResult<String> validate(@PathVariable String id) {
        try {
            ArchivePolicy policy = policyService.get(id);
            if (policy == null) {
                return ApiResult.fail(ArchiveException.VALIDATION_ERROR, "归档策略不存在：" + id);
            }
            policyService.validateForEnable(policy);
            return ApiResult.success("校验通过");
        } catch (ArchiveException e) {
            log.warn("策略校验未通过 policyId={}：{}", id, e.getMessage());
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/enable/{id}", method = RequestMethod.POST)
    public ApiResult<ArchivePolicy> enable(@PathVariable String id) {
        try {
            return ApiResult.success(policyService.enable(id));
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/disable/{id}", method = RequestMethod.POST)
    public ApiResult<ArchivePolicy> disable(@PathVariable String id) {
        try {
            return ApiResult.success(policyService.disable(id));
        } catch (ArchiveException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    // ==================== 轻量参数解析（自持，不依赖 BaseController） ====================

    private PageQueryRequest buildPageQueryRequest(Map<String, Object> body) {
        PageQueryRequest request = new PageQueryRequest();
        if (body != null) {
            Object pageNum = body.get("pageNum");
            Object pageSize = body.get("pageSize");
            Object orderBy = body.get("orderBy");
            if (pageNum instanceof Number n) {
                request.setPageNum(n.intValue());
            }
            if (pageSize instanceof Number n) {
                request.setPageSize(n.intValue());
            }
            if (orderBy instanceof String s && !s.isBlank()) {
                request.setOrderBy(s);
            }
        }
        if (request.getPageNum() <= 0) {
            request.setPageNum(1);
        }
        if (request.getPageSize() <= 0) {
            request.setPageSize(20);
        }
        if (request.getOrderBy() == null || request.getOrderBy().isBlank()) {
            request.setOrderBy(DEFAULT_ORDER_BY);
        }
        return request;
    }

    /** 扁平等值过滤：把 body 中指定的白名单字段（非空时）转为 FilterGroup */
    private FilterGroup buildFilterGroup(Map<String, Object> body, String... allowedFields) {
        FilterGroup filterGroup = new FilterGroup();
        if (body != null) {
            for (String field : allowedFields) {
                Object value = body.get(field);
                if (value instanceof String s && !s.isBlank()) {
                    filterGroup.addFilter(field, s);
                } else if (value instanceof Number n) {
                    filterGroup.addFilter(field, String.valueOf(n));
                }
            }
        }
        return filterGroup;
    }
}
