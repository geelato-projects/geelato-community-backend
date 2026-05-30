package cn.geelato.orm.executor;

@FunctionalInterface
interface SupplierWithException<T> {
    T get();
}
