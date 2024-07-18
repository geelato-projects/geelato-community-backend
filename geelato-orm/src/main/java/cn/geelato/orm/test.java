package cn.geelato.orm;


import cn.geelato.orm.query2.DefaultOperator;
import cn.geelato.orm.query2.Operator;

import java.util.List;

public class test {
    public static void main(String[] args){
        Operator operator=new DefaultOperator();
        List<Object> list= operator.query("")
                .select("id")
                .where("id=1")
                .pageQueryList();
    }
}
