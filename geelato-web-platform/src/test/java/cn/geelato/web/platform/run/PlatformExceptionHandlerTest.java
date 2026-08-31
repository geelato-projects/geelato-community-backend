package cn.geelato.web.platform.run;

import cn.geelato.core.orm.SqlExecuteException;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.srv.auth.AuthBadRequestException;
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

    private final PlatformExceptionHandler handler = new PlatformExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

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
        // data：logTag 已生成且与 msg 中凭据一致；errorMsg 为友好文案（logStack 默认关闭）
        assertNotNull(body.getData().getLogTag());
        assertTrue(body.getMsg().contains(body.getData().getLogTag()));
        assertFalse(body.getData().getErrorMsg().contains("select * from"));
        assertTrue(body.getData().getErrorMsg().contains("数据操作失败"));
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
    void fallbackExceptionReturnsSystemBusyWithLogTag() {
        ResponseEntity<?> entity = handler.handleOtherException(new NullPointerException("secret internal detail"), request);

        // 兜底保持历史 HTTP 400
        assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
        ApiResult<PlatformErrorResult> body = (ApiResult<PlatformErrorResult>) entity.getBody();
        assertNotNull(body);
        assertTrue(body.getMsg().contains("系统繁忙，请稍后重试"));
        assertTrue(body.getMsg().contains("反馈凭据"));
        // 不透出原始异常消息
        assertFalse(body.getMsg().contains("secret internal detail"));
        assertEquals(50001, body.getData().getErrorCode());
        assertNotNull(body.getData().getLogTag());
        assertTrue(body.getMsg().contains(body.getData().getLogTag()));
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
