package cn.geelato.web.platform.resolve.model;

import lombok.Data;

import java.util.List;

@Data
public class MappingSuggestion {
    private String key;
    private String rawValue;
    private List<MappingCandidate> candidates;
    private AiSuggestion suggested;
    private MappingCandidate selected;
}

