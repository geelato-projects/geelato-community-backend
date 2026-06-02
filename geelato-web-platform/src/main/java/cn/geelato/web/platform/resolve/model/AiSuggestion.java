package cn.geelato.web.platform.resolve.model;

import lombok.Data;

@Data
public class AiSuggestion {
    private String id;
    private Double confidence;
    private String reason;
}

