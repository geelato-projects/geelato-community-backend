package cn.geelato.web.platform.resolve.biz.impl;

import org.springframework.stereotype.Component;

@Component
public class QuoteSheetParseBizHandler extends AbstractJsonResolveBizHandler {
    @Override
    public String biztag() {
        return "quote.sheet.parse";
    }
}

