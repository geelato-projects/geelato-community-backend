package cn.geelato.orm;

/**
 * 函数式接口，用于结果包装
 * 支持将查询结果转换为指定类型
 * 
 * @param <T> 输入类型
 * @param <R> 输出类型
 * @author geelato
 * @version 1.0
 */
@FunctionalInterface
public interface WrapperResultFunction<T, R> {
    
    /**
     * 应用函数，将输入转换为输出
     * @param input 输入参数
     * @return 转换后的结果
     */
    R apply(T input);
}