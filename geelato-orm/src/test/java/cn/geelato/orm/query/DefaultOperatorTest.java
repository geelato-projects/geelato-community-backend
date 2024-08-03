package cn.geelato.orm.query;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DefaultOperatorTest {

    @Test
    public void query() {
        Operator operator=new DefaultOperator();
        List<Object> list= operator.query("")
                .select("id")
                .where("id=1")
                .pageQueryList();
    }
}