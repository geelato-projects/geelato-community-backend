package cn.geelato.web.platform.resolve.biz.impl;

import org.springframework.stereotype.Component;

@Component
public class CarrierSiParseBizHandler extends AbstractJsonResolveBizHandler {
    @Override
    public String biztag() {
        return "carrier.si.parse";
    }
}

