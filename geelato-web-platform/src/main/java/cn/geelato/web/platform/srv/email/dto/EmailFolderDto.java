package cn.geelato.web.platform.srv.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EmailFolderDto {
    private String name;
    private String fullName;
    private String parentFullName;
    private Boolean holdsMessages;
    private Boolean holdsFolders;
    private List<EmailFolderDto> children;
}
