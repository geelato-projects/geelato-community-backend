package cn.geelato.orm;

import cn.geelato.orm.querydsl.DefaultOperator;
import cn.geelato.orm.querydsl.Operator;
import cn.geelato.orm.querydsl.ResultWrappers;

import static cn.geelato.orm.querydsl.ResultWrapper.lowerCase;

public class test {
    public static void main(String[] args){
        Operator operator=new DefaultOperator();
        operator.query("")
                .select("id")
                .where("id=1")
                .fetch(lowerCase(ResultWrappers.singleMap()))
                .sync();

    }
}
