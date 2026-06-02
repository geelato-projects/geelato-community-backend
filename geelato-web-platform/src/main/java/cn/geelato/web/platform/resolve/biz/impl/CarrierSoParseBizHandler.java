package cn.geelato.web.platform.resolve.biz.impl;

import org.springframework.stereotype.Component;

@Component
public class CarrierSoParseBizHandler extends AbstractJsonResolveBizHandler {
    @Override
    public String biztag() {
        return "carrier.so.parse";
    }
}

