package cn.geelato.it.support.replaydiff;

public record JsonDiffEntry(String path, String kind, String left, String right) {
}
