package cn.geelato.script.hostfixture.util;

/** 模拟第三方实例类（无参构造，实例方法）。 */
public class DateLikeService {

    public String now() {
        return "NOW";
    }

    public int add(int a, int b) {
        return a + b;
    }
}
