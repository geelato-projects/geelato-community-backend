package cn.geelato.web.platform.srvlog.web;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.IgnoreSrvLog;
import cn.geelato.web.platform.srvlog.model.SrvExceptionSummary;
import cn.geelato.web.platform.srvlog.model.SrvLogPage;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;
import cn.geelato.web.platform.srvlog.service.SrvLogService;
import cn.geelato.web.platform.srvlog.spi.SrvLogQueryOptions;
import cn.geelato.web.platform.srvlog.spi.SrvSummaryQueryOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@IgnoreSrvLog
@ApiRestController("/srv-log")
public class SrvLogQueryController {
    private final SrvLogService service;

    public SrvLogQueryController(SrvLogService service) {
        this.service = service;
    }

    @GetMapping("/exceptions")
    public ApiPagedResult<List<SrvLogRecord>> exceptions(@RequestParam(value = "methodKey", required = false) String methodKey,
                                                         @RequestParam(value = "httpMethod", required = false) String httpMethod,
                                                         @RequestParam(value = "pathPattern", required = false) String pathPattern,
                                                         @RequestParam(value = "startTime", required = false) Long startTime,
                                                         @RequestParam(value = "endTime", required = false) Long endTime,
                                                         @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                                         @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        String mk = resolveMethodKey(methodKey, httpMethod, pathPattern);
        if (mk == null) {
            return ApiPagedResult.fail("methodKey 或 httpMethod+pathPattern 必填");
        }
        SrvLogQueryOptions options = new SrvLogQueryOptions();
        options.setStartTime(startTime);
        options.setEndTime(endTime);
        options.setPage(page);
        options.setSize(size);
        SrvLogPage p = service.listExceptions(mk, options);
        List<SrvLogRecord> data = p == null ? List.of() : p.getRecords();
        long total = p == null ? 0 : p.getTotal();
        int dataSize = data == null ? 0 : data.size();
        return ApiPagedResult.success(data, page, size, dataSize, total);
    }

    @GetMapping("/exceptions/recent")
    public ApiResult<List<SrvExceptionSummary>> recent(@RequestParam("days") int days,
                                                       @RequestParam(value = "topN", required = false, defaultValue = "200") int topN) {
        if (days <= 0) {
            return ApiResult.fail("days 必须大于 0");
        }
        SrvSummaryQueryOptions options = new SrvSummaryQueryOptions();
        options.setTopN(topN);
        return ApiResult.success(service.listRecentExceptionSummary(days, options));
    }

    private String resolveMethodKey(String methodKey, String httpMethod, String pathPattern) {
        if (methodKey != null && !methodKey.isBlank()) {
            return methodKey.trim();
        }
        if (httpMethod == null || httpMethod.isBlank()) {
            return null;
        }
        if (pathPattern == null || pathPattern.isBlank()) {
            return null;
        }
        return httpMethod.trim().toUpperCase() + " " + pathPattern.trim();
    }
}

