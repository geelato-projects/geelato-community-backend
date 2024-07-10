package cn.geelato.orm.querydsl;

public interface ExceptionTranslation extends Feature {
    String ID_VALUE = "exceptionTranslation";

    FeatureId<ExceptionTranslation> ID = FeatureId.of(ID_VALUE);

    Throwable translate(Throwable e);
}
