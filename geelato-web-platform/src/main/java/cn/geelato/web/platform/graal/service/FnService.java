package cn.geelato.web.platform.graal.service;

import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.UserService;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.util.StringUtils;
import cn.geelato.utils.NumbChineseUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@GraalService(name="fn",built = "true")
public class FnService {

    public User getUser(String userId){
        UserService userService=new UserService();
        return userService.getModel(User.class,userId);
    }

    public String toChineseCurrency(String digit){
        return NumbChineseUtils.byOldChinese(digit);
    }

    public String dateText(String targetFormat,String dateStr) throws ParseException {
        String formatDate=null;
        Date date=null ;
        SimpleDateFormat targetDateFormat=new SimpleDateFormat(targetFormat);
        if(StringUtils.isEmpty(dateStr)){
            date=new Date();
        }else{
            date = targetDateFormat.parse(dateStr);
        }
        formatDate=targetDateFormat.format(date);
        return formatDate;
    }
}
