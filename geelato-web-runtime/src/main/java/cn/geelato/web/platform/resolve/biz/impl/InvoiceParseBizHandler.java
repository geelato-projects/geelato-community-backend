package cn.geelato.web.platform.resolve.biz.impl;

import org.springframework.stereotype.Component;

@Component
public class InvoiceParseBizHandler extends AbstractJsonResolveBizHandler {
    @Override
    public String biztag() {
        return "invoice.parse";
    }
}

