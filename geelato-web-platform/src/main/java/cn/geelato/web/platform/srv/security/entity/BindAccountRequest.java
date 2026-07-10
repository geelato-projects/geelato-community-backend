package cn.geelato.web.platform.srv.security.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BindAccountRequest {
    @NotBlank
    @Size(max = 8)
    private String validType;
    @NotBlank
    @Size(max = 64)
    private String userId;
    @NotBlank
    @Size(max = 128)
    private String authCode;
    @NotBlank
    @Size(max = 128)
    private String validBox;
    @Size(max = 16)
    private String prefix;
}
