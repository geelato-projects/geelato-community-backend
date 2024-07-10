package cn.geelato.web.platform.m.excel.entity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author diabl
 * @date 2024/1/10 11:45
 */
public class WordIndexTagMeta {
    private int startIndex = -1;
    private int endIndex = -1;
    private Set<WordIndexTagMeta> indexMetas = new LinkedHashSet<>();

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public Set<WordIndexTagMeta> getIndexMetas() {
        return indexMetas;
    }

    public void setIndexMetas(Set<WordIndexTagMeta> indexMetas) {
        this.indexMetas = indexMetas;
    }
}
