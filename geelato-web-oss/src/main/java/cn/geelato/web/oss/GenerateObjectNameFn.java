package cn.geelato.web.oss;

@FunctionalInterface
public interface GenerateObjectNameFn {
    String generateObjectName(FileMeta fileMeta);
}