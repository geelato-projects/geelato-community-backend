package cn.geelato.web.platform.resolve.model;

import lombok.Getter;

@Getter
public enum ResolveStatusEnum {
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed");

    private final String value;

    ResolveStatusEnum(String value) {
        this.value = value;
    }
}

