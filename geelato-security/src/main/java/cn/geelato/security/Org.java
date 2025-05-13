package cn.geelato.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Org extends OrgCore {
    private String code;
    private String type;
    private String category;
    private int status;
    private String description;
}
