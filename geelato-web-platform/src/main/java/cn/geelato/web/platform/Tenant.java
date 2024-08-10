package cn.geelato.web.platform;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Tenant {
    public Tenant(String code){
        this.code=code;
    }
    private String code;
    private String name;

}
