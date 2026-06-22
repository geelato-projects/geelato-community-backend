package cn.geelato.web.platform.srv.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportApiResponse<T> {
    private String msg;
    private int code;
    private T data;
    private String status;

    public static <T> ReportApiResponse<T> success(T data) {
        ReportApiResponse<T> response = new ReportApiResponse<>();
        response.setCode(20000);
        response.setMsg("success");
        response.setStatus("success");
        response.setData(data);
        return response;
    }

    public static <T> ReportApiResponse<T> error(String msg) {
        ReportApiResponse<T> response = new ReportApiResponse<>();
        response.setCode(50000);
        response.setMsg(msg == null || msg.isBlank() ? "error" : msg);
        response.setStatus("error");
        return response;
    }
}
