package cn.geelato.orm.querydsl;

public class SingleResultWrapper<T> implements ResultWrapper<T, T> {
    private ResultWrapper<T, ?> wrapper;

    public SingleResultWrapper(ResultWrapper<T, ?> wrapper) {
        this.wrapper = wrapper;
    }
    @Override
    public T newRowInstance() {
        return wrapper.newRowInstance();
    }
}
