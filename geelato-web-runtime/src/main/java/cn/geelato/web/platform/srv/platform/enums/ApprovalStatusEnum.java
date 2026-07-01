package cn.geelato.web.platform.srv.platform.enums;

import lombok.Getter;

@Getter
public enum ApprovalStatusEnum {
    DRAFT("待审批", "draft"),
    VERIFY("审批", "verify"),
    AGREE("同意", "agree"),
    REJECT("拒绝", "reject");

    private final String label;// 选项内容
    private final String value;// 选项值

    ApprovalStatusEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
