package cn.geelato.lang.api;

import lombok.Getter;

@Getter
public enum ResultCode {
    //todo，为了暂时不影响前端，先保留原先定义20000和-2的状态码
    RC200(20000,"Success"),

    RC403(403,"Not Authorized"),

    RC500(-2,"Fail");

    private final int code;

    private final String message;

    ResultCode(int code, String message){
        this.code = code;
        this.message = message;
    }

}
