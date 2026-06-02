package cn.geelato.web.platform.resolve.model;

import lombok.Data;

@Data
public class MappingCandidate {
    private String id;
    private String code;
    private String name;
    private Object extra;
}

