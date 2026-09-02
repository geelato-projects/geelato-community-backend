package cn.geelato.web.platform.errorlog.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.PlatformExceptionLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionLogServiceTest {

    @Test
    void recordNeverThrowsWhenDaoFails() {
        Dao dao = mock(Dao.class);
        when(dao.save(any(PlatformExceptionLog.class))).thenThrow(new RuntimeException("db down"));
        ExceptionLogService service = new ExceptionLogService(dao);

        PlatformExceptionLog record = new PlatformExceptionLog();
        record.setId("123456789");
        record.setExceptionCode("10002");

        // 落库失败降级写文件，异常处理路径绝不抛出
        assertDoesNotThrow(() -> service.record(record));
    }

    @Test
    void recordNeverThrowsAfterShutdown() {
        Dao dao = mock(Dao.class);
        ExceptionLogService service = new ExceptionLogService(dao);
        service.shutdown();

        PlatformExceptionLog record = new PlatformExceptionLog();
        record.setId("123456789");

        // 线程池已关闭（拒绝执行）同样降级，不抛出
        assertDoesNotThrow(() -> service.record(record));
    }

    @Test
    void recordIgnoresNull() {
        ExceptionLogService service = new ExceptionLogService(mock(Dao.class));
        assertDoesNotThrow(() -> service.record(null));
    }
}
