package cn.geelato.web.platform.srv.security.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgetPasswordRequest {
    @NotBlank
    @Size(max = 64)
    private String action;
    @NotBlank
    @Size(max = 8)
    private String validType;
    @Size(max = 16)
    private String prefix;
    @NotBlank
    @Size(max = 128)
    private String validBox;
    @NotBlank
    @Size(max = 32)
    private String authCode;
    @NotBlank
    @Size(max = 64)
    private String userId;
    @NotBlank
    @Size(max = 128)
    private String password;
}
