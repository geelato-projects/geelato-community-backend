package cn.geelato.archive.service;

import cn.geelato.archive.entity.ArchiveRun;
import cn.geelato.archive.enums.RunStatusEnum;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiPagedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 归档运行记录服务：run 生命周期管理与查询。
 * <p>不继承 web-platform 的 BaseService（模块自包含），直接基于 Dao；
 * 公共字段在无宿主字段填充器时由本类兜底填充。</p>
 */
@Component
@Slf4j
public class ArchiveRunService {

    private final Dao dao;

    @Autowired
    public ArchiveRunService(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    /** 创建 running 状态的运行记录 */
    public ArchiveRun startRun(String policyId, Date timeWatermark, String executionMode) {
        ArchiveRun run = new ArchiveRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setPolicyId(policyId);
        run.setStatus(RunStatusEnum.RUNNING.name());
        run.setTimeWatermark(timeWatermark);
        run.setExecutionMode(executionMode);
        run.setArchivedRows(0L);
        run.setBatches(0);
        run.setCostMs(0L);
        run.setBeginAt(new Date());
        fillBaseFields(run, "archive-engine");
        dao.insert(run);
        return run;
    }

    /** 批次推进后更新进度（行数/批次/游标） */
    public void updateProgress(ArchiveRun run) {
        run.setUpdateAt(new Date());
        dao.update(run);
    }

    public void finishSuccess(ArchiveRun run, long rows, int batches, long costMs, String message) {
        run.setStatus(RunStatusEnum.SUCCESS.name());
        run.setArchivedRows(rows);
        run.setBatches(batches);
        run.setCostMs(costMs);
        run.setEndAt(new Date());
        run.setMessage(message);
        run.setUpdateAt(new Date());
        dao.update(run);
    }

    public void finishCanceled(ArchiveRun run, long rows, int batches, long costMs, String message) {
        run.setStatus(RunStatusEnum.CANCELED.name());
        run.setArchivedRows(rows);
        run.setBatches(batches);
        run.setCostMs(costMs);
        run.setEndAt(new Date());
        run.setMessage(message);
        run.setUpdateAt(new Date());
        dao.update(run);
    }

    /** 失败收尾：errorJson 由引擎组装好（含表/批次/游标/期望实际行数/堆栈），此处只落库 */
    public void finishFailed(ArchiveRun run, String message, String errorJson) {
        run.setStatus(RunStatusEnum.FAILED.name());
        run.setEndAt(new Date());
        run.setMessage(truncate(message, 1000));
        run.setErrorJson(errorJson);
        run.setUpdateAt(new Date());
        dao.update(run);
    }

    /** 同策略是否已有运行中的 run（并发保护） */
    public boolean hasRunning(String policyId) {
        FilterGroup fg = new FilterGroup();
        fg.addFilter("policyId", policyId);
        fg.addFilter("status", RunStatusEnum.RUNNING.name());
        List<ArchiveRun> runs = dao.queryList(ArchiveRun.class, fg, null);
        return runs != null && !runs.isEmpty();
    }

    public ArchiveRun get(String id) {
        return dao.queryForObject(ArchiveRun.class, id);
    }

    public ApiPagedResult pageQuery(FilterGroup filterGroup, PageQueryRequest request) {
        return dao.pageQueryResult(ArchiveRun.class, filterGroup, request);
    }

    private void fillBaseFields(ArchiveRun run, String operator) {
        Date now = new Date();
        if (run.getCreateAt() == null) {
            run.setCreateAt(now);
        }
        if (run.getCreator() == null || run.getCreator().isBlank()) {
            run.setCreator(operator);
        }
        if (run.getUpdater() == null || run.getUpdater().isBlank()) {
            run.setUpdater(operator);
        }
        if (run.getUpdateAt() == null) {
            run.setUpdateAt(now);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
