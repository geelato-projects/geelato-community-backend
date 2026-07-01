package cn.geelato.web.platform.resolve.biz.impl;

import org.springframework.stereotype.Component;

@Component
public class BookingConfirmParseBizHandler extends AbstractJsonResolveBizHandler {
    @Override
    public String biztag() {
        return "booking.confirm.parse";
    }
}

