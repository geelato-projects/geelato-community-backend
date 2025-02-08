package cn.geelato.reactor;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.crypto.Data;

@RestController
public class AddDataController {
    @RequestMapping("/addData")

    public void addData(String data) {
        System.out.println(data);
    }
}
