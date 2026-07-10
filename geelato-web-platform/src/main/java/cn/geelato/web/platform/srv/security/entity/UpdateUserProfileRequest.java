package cn.geelato.web.platform.srv.security.entity;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserProfileRequest {
    @Size(max = 64)
    private String name;
    @Size(max = 64)
    private String enName;
    @Size(max = 16)
    private String mobilePrefix;
    @Size(max = 16)
    private String mobilePhone;
    @Size(max = 64)
    private String telephone;
    @Size(max = 128)
    private String email;
    @Size(max = 32)
    private String nationCode;
    @Size(max = 32)
    private String provinceCode;
    @Size(max = 32)
    private String cityCode;
    @Size(max = 255)
    private String address;
    @Size(max = 255)
    private String description;
}
