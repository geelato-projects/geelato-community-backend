package cn.geelato.core.orm;

import org.junit.Test;
import org.springframework.util.Assert;

/**
 * @author geemeta
 */
public class SqlFilesTest {

    @Test
    public void parseLine() {
        String line1 = "insert into platform_city(id, creator, create_at, updater, update_at, description,code, name, province_code) values ($newId(), 1, NOW(), 1, NOW(),'','630100', '西宁市', '630000');";
        String newLine1 = SqlFiles.parseLine(line1);
//        System.out.println("parseLine>无空格$newId()>" + newLine1);
        Assert.isTrue(newLine1.indexOf("\\$newId") == -1, "未能完成方法$newId()的执行，无法生成id。");

        String line2 = "insert into platform_city(id, creator, create_at, updater, update_at, description,code, name, province_code) values ($newId  (  ), 1, NOW(), 1, NOW(),'','630100', '西宁市', '630000');";
        String newLine2 = SqlFiles.parseLine(line2);
//        System.out.println("parseLine>有空格$newId  (  )>" + newLine2);
        Assert.isTrue(newLine2.indexOf("\\$newId") == -1, "未能完成方法$newId  (  )的执行，无法生成id。");
    }

}