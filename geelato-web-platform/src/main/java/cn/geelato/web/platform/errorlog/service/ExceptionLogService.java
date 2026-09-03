package cn.geelato.web.platform.errorlog.service;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.filter.FilterGroup.Operator;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.meta.PlatformExceptionLog;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平台错误日志服务：把全局异常处理器生成的记录（id=logTag 反馈凭据）异步落库
 * {@code platform_exception_log}（catalog=platform-log，经 dynamicDao 按 catalog 路由）。
 *
 */
@Slf4j
@Service
public class ExceptionLogService {

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private final Dao dao;
    private final ExecutorService executor;

    public ExceptionLogService(@Qualifier("dynamicDao") Dao dao) {
        this.dao = dao;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "exception-log-" + THREAD_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 异步落库（运维数据始终全量记录，不受前端 LogStack 开关影响）；失败降级写文件，绝不抛出。
     */
    public void record(PlatformExceptionLog record) {
        if (record == null) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    dao.insert(record);
                } catch (Exception e) {
                    log.warn("错误日志落库失败，降级写文件: logTag={}", record.getId(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池已关闭（应用停机中），降级写文件，绝不抛出
            log.warn("错误日志线程池已关闭，降级写文件: logTag={}", record.getId(), e);
        }
    }

    /**
     * 按反馈凭据（=主键）精确查询，运维排障主入口。
     */
    public PlatformExceptionLog findByTag(String logTag) {
        if (!StringUtils.hasText(logTag)) {
            return null;
        }
        FilterGroup fg = new FilterGroup();
        fg.addFilter("id", Operator.eq, logTag);
        List<PlatformExceptionLog> list = dao.queryList(PlatformExceptionLog.class, fg, null);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    /**
     * 分页查询（列表页剥除堆栈大字段由调用方处理）。
     */
    public ApiPagedResult<PlatformExceptionLog> page(String exceptionCode, String appId, String tenantCode,
                                                     Long fromTime, Long toTime, int pageNum, int pageSize) {
        FilterGroup fg = new FilterGroup();
        if (StringUtils.hasText(exceptionCode)) {
            fg.addFilter("exceptionCode", Operator.eq, exceptionCode);
        }
        if (StringUtils.hasText(appId)) {
            fg.addFilter("appId", Operator.eq, appId);
        }
        if (StringUtils.hasText(tenantCode)) {
            fg.addFilter("tenantCode", Operator.eq, tenantCode);
        }
        if (fromTime != null) {
            fg.addFilter("happenedTime", Operator.gte, String.valueOf(fromTime), new Date(fromTime));
        }
        if (toTime != null) {
            fg.addFilter("happenedTime", Operator.lte, String.valueOf(toTime), new Date(toTime));
        }
        PageQueryRequest pageReq = new PageQueryRequest();
        pageReq.setPageNum(Math.max(pageNum, 1));
        pageReq.setPageSize(Math.max(Math.min(pageSize, 200), 1));
        pageReq.setOrderBy("happened_time desc");
        return dao.pageQueryResult(PlatformExceptionLog.class, fg, pageReq);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
