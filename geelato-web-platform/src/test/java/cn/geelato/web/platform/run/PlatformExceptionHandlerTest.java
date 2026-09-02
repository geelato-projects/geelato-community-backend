package cn.geelato.web.platform.run;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.orm.SqlExecuteException;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.errorlog.service.ExceptionLogService;
import cn.geelato.web.platform.srv.auth.AuthBadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformExceptionHandlerTest {

    private final PlatformExceptionHandler handler = new PlatformExceptionHandler(org.mockito.Mockito.mock(ExceptionLogService.class));
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @AfterEach
    void restoreLogStack() {
        GlobalContext.setLogStack(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sqlExecuteExceptionReturnsFriendlyMessageWithoutTechnicalDetails() {
        SqlExecuteException ex = new SqlExecuteException(
                new UncategorizedDataAccessException("query failed",
                        new SQLException("You have an error in your SQL syntax", "42000", 1064)) {
                },
                "select * from platform_dev_table where id = ?", new Object[]{"123"});

        ResponseEntity<?> entity = handler.handleException(ex, request);

        // SQL_EXECUTE 未声明 httpStatus，默认 500
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) entity.getBody();
        assertNotNull(body);
        assertEquals("fail", body.getStatus());
        // 顶层 msg：友好文案 + 错误码 + 反馈凭据，不含 SQL 语句与参数
        assertTrue(body.getMsg().contains("数据操作失败，请稍后重试"));
        assertTrue(body.getMsg().contains("错误码 10002"));
        assertTrue(body.getMsg().contains("反馈凭据"));
        assertFalse(body.getMsg().contains("select * from"));
        assertFalse(body.getMsg().contains("123"));
        // data：logTag 已生成且与 msg 中凭据一致；errorMsg 为友好文案（不含 SQL/参数）
        assertNotNull(body.getData().getLogTag());
        assertTrue(body.getMsg().contains(body.getData().getLogTag()));
        assertFalse(body.getData().getErrorMsg().contains("select * from"));
        assertTrue(body.getData().getErrorMsg().contains("数据操作失败"));
        // stackTraceDetail：LogStack 默认开启，技术详情（含 SQL 的异常消息）+ 完整堆栈随响应下发
        assertTrue(body.getData().getStackTraceDetail().contains("select * from platform_dev_table"));
        assertTrue(body.getData().getStackTraceDetail().contains("\tat "));
    }

    @Test
    @SuppressWarnings("unchecked")
    void logStackDisabledHidesStackTraceDetailOnly() {
        SqlExecuteException ex = new SqlExecuteException(
                new UncategorizedDataAccessException("query failed",
                        new SQLException("You have an error in your SQL syntax", "42000", 1064)) {
                },
                "select * from platform_dev_table where id = ?", new Object[]{"123"});
        GlobalContext.setLogStack(false);

        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) handler.handleException(ex, request).getBody();

        assertNotNull(body);
        // 关闭开关：仅隐藏 stackTraceDetail；msg（友好文案+凭据）不受影响
        assertEquals("", body.getData().getStackTraceDetail());
        assertTrue(body.getMsg().contains("数据操作失败，请稍后重试"));
        assertTrue(body.getMsg().contains("反馈凭据"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void businessCoreExceptionKeepsOriginalMessageWithFeedbackTag() {
        AuthBadRequestException ex = new AuthBadRequestException("验证码错误");

        ResponseEntity<?> entity = handler.handleException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) entity.getBody();
        assertNotNull(body);
        // 业务异常文案原样保留，末尾追加排障凭据
        assertTrue(body.getMsg().contains("验证码错误"));
        assertTrue(body.getMsg().contains("错误码 20003"));
        assertTrue(body.getMsg().contains("反馈凭据"));
        assertNotNull(body.getData().getLogTag());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallbackExceptionReturnsDetailedMessageWithLogTag() {
        ResponseEntity<?> entity = handler.handleOtherException(new NullPointerException("secret internal detail"), request);

        // 兜底保持历史 HTTP 400
        assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) entity.getBody();
        assertNotNull(body);
        // 兜底透出详细错误消息（不再吞成"系统繁忙"），并追加反馈凭据
        assertTrue(body.getMsg().contains("secret internal detail"));
        assertTrue(body.getMsg().contains("反馈凭据"));
        assertEquals(50001, body.getData().getErrorCode());
        assertNotNull(body.getData().getLogTag());
        assertTrue(body.getMsg().contains(body.getData().getLogTag()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallbackExceptionWithoutMessageExposesExceptionClassName() {
        ResponseEntity<?> entity = handler.handleOtherException(new NullPointerException(), request);

        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) entity.getBody();
        assertNotNull(body);
        // 无消息异常（如裸 NPE）透出"系统异常：类名"，提供定位线索
        assertTrue(body.getMsg().contains("系统异常：NullPointerException"));
        assertTrue(body.getMsg().contains("反馈凭据"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void illegalArgumentKeepsBusinessMessage() {
        ResponseEntity<?> entity = handler.handleOtherException(new IllegalArgumentException("id不能为空"), request);

        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) entity.getBody();
        assertNotNull(body);
        assertTrue(body.getMsg().contains("id不能为空"));
        assertTrue(body.getMsg().contains("反馈凭据"));
    }
}
