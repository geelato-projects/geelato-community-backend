package cn.geelato.web.platform.resolve.model;

import lombok.Data;

import java.util.List;

@Data
public class ResolveDraftUpdateRequest {
    private ExtractedStructuredData extracted;
    private List<MappingSuggestion> mapping;
}

