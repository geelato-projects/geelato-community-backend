package cn.geelato.web.platform.srv.security.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class LoginParams {
    @NotBlank
    @Size(max = 64)
    private String username;
    @NotBlank
    @Size(max = 128)
    private String password;
    @Size(max = 64)
    private String org;
    @Size(max = 64)
    private String tenant;
    @Size(max = 64)
    private String suffix;
}
