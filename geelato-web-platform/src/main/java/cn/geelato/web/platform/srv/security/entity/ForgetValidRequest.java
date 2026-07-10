package cn.geelato.web.platform.srv.security.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgetValidRequest {
    @NotBlank
    @Size(max = 8)
    private String validType;
    @Size(max = 16)
    private String prefix;
    @NotBlank
    @Size(max = 128)
    private String validBox;
}
