import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.J2Cache;
import cn.geelato.web.platform.boot.BootApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * @author geelato
 */

@RunWith(SpringRunner.class)
@SpringBootTest(classes = BootApplication.class)
@WebAppConfiguration
public class CacheTest {


    @Test
    public void test() {
        CacheChannel cache = J2Cache.getChannel();
        //缓存操作
        cache.set("default", "1", "Hello J2Cache");
        System.out.println(cache.get("default", "1"));
        cache.evict("default", "1");
        System.out.println(cache.get("default", "1"));

        cache.close();
    }

    public static void main(String[] args) {

    }
}
