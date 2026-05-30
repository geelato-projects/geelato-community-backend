package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailFolderDto {
    private String name;
    private String fullName;
    private Boolean holdsMessages;
    private Boolean holdsFolders;
}

