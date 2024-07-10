package cn.geelato.orm.querydsl;

public interface ResultWrapper<E, R> {
    E newRowInstance();
    public static <E, R> ResultWrapper<E, R> lowerCase(ResultWrapper<E, R> wrapper) {
        return LowerCaseColumnResultWrapper.of(wrapper);
    }
}
