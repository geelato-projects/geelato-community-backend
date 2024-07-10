package cn.geelato.core;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author geemeta
 */
public class TestHelper {

    public static String getText(String resUrl){
        URL url = TestHelper.class.getClassLoader().getResource(resUrl);
        List<String> list = null;
        try {
            list = Files.readAllLines(Paths.get(url.toURI()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        StringBuilder sb = new StringBuilder();
        for (String line : list) {
            sb.append(line);
        }
        return sb.toString();
    }
}
