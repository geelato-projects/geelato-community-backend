package cn.geelato.web.platform.srv.ocr.entity;

import cn.geelato.plugin.ocr.PDFAnnotationDiscernRule;
import cn.geelato.plugin.ocr.UnionType;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;

import java.lang.reflect.Type;

public class PDFAnnotationDiscernRuleDeserializer implements ObjectDeserializer {

    @Override
    public <T> T deserialze(DefaultJSONParser defaultJSONParser, Type type, Object o) {
        JSONObject jsonObject = defaultJSONParser.parseObject();
        PDFAnnotationDiscernRule rule = new PDFAnnotationDiscernRule();
        rule.setBem(jsonObject.getString("bem"));
        rule.setLem(jsonObject.getString("lem"));
        rule.setRem(jsonObject.getString("rem"));
        rule.setUnionType(jsonObject.getObject("unionType", UnionType.class));
        rule.setDiscernWidth(jsonObject.getInteger("discernWidth") != null ? jsonObject.getInteger("discernWidth") : 0); // 设置默认值
        return (T) rule;
    }

    @Override
    public int getFastMatchToken() {
        return 0;
    }
}
