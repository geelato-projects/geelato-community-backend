package cn.geelato.orm.querydsl;

import java.util.Map;

public class ResultWrappers {
    public static ResultWrapper<Map<String, Object>, Map<String, Object>> singleMap() {
        return single(map());
    }

    public static <E> ResultWrapper<E, E> single(ResultWrapper<E, ?> wrapper) {
        return new SingleResultWrapper<>(wrapper);
    }


}
