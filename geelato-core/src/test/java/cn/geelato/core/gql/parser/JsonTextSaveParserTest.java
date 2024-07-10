package cn.geelato.core.gql.parser;

import cn.geelato.core.TestHelper;
import cn.geelato.core.meta.model.entity.DemoEntity;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.Ctx;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.URISyntaxException;

@RunWith(SpringRunner.class)
public class JsonTextSaveParserTest {


    @Test
    public void parse1() throws IOException, URISyntaxException {
        MetaManager.singleInstance().parseOne(DemoEntity.class);  //当前为解析java实体类，要改造为解析数据库中的模型数据
        String json = TestHelper.getText("./gql/parser/saveJsonText1.json");
        SaveCommand saveCommand = new JsonTextSaveParser().parse(json, new Ctx());
        Assert.assertEquals(1, saveCommand.getCommands().size());
    }

    @Test
    public void parse2() throws IOException, URISyntaxException {
        MetaManager.singleInstance().parseOne(DemoEntity.class);
        String json = TestHelper.getText("./gql/parser/saveJsonText2.json");
        SaveCommand saveCommand = new JsonTextSaveParser().parse(json, new Ctx());
        Assert.assertEquals(2, saveCommand.getCommands().size());
    }

    @Test
    public void parse3() throws IOException, URISyntaxException {
        MetaManager.singleInstance().parseOne(DemoEntity.class);
        // 三层
        String json = TestHelper.getText("./gql/parser/saveJsonText3.json");
        SaveCommand saveCommand = new JsonTextSaveParser().parse(json, new Ctx());
        // 第二层两个
        Assert.assertEquals(2, saveCommand.getCommands().size());
        // 第二层的第二个有一个子项，即第三层有一个
        Assert.assertEquals(1, saveCommand.getCommands().get(1).getCommands().size());
    }



}