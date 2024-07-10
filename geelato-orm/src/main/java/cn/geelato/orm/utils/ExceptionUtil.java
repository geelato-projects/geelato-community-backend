package cn.geelato.orm.utils;

import cn.geelato.orm.querydsl.ExceptionTranslation;
import cn.geelato.orm.querydsl.FeatureSupportedMetadata;
import lombok.SneakyThrows;

import java.util.function.Supplier;

public class ExceptionUtil {

    public static Throwable translation(FeatureSupportedMetadata metadata, Throwable e) {
        return metadata.findFeature(ExceptionTranslation.ID)
                .map(trans -> trans.translate(e))
                .orElse(e);
    }
    @SneakyThrows
    public static <T> T translation(Supplier<T> supplier, FeatureSupportedMetadata metadata) {
        try {
            return supplier.get();
        } catch (Throwable r) {
            throw translation(metadata, r);
        }
    }
}
