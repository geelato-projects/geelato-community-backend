package cn.geelato.web.platform.resolve.model;

import lombok.Data;

@Data
public class ExtractedField {
    private String key;
    private String label;
    private String value;
    private Integer page;
    private Object bbox;
    private Double confidence;
}

