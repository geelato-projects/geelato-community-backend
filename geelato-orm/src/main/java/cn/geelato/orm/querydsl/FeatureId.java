package cn.geelato.orm.querydsl;

public interface FeatureId<T extends Feature> {

    String getId();

    static <T extends Feature> FeatureId<T> of(String id) {
        return () -> id;
    }
}