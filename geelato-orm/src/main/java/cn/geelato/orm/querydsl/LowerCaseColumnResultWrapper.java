package cn.geelato.orm.querydsl;

import lombok.AllArgsConstructor;

@AllArgsConstructor(staticName = "of")
public class LowerCaseColumnResultWrapper<E, R> implements ResultWrapper<E, R>{

    private ResultWrapper<E, R> wrapper;
    @Override
    public E newRowInstance() {
        return wrapper.newRowInstance();
    }
}
