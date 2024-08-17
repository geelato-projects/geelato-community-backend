package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author diabl
 */
@Getter
@Setter
public class WordIndexTagMeta {
    private int startIndex = -1;
    private int endIndex = -1;
    private Set<WordIndexTagMeta> indexMetas = new LinkedHashSet<>();
}
