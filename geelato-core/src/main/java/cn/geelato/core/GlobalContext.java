package cn.geelato.core;

@SuppressWarnings("ALL")
public class GlobalContext {
    protected final static String __Environment__="development";
    protected final static Boolean  __CACHE__=__Environment__.equals("product");
    public static String getEnvironment(){
        return __Environment__;
    }
}
