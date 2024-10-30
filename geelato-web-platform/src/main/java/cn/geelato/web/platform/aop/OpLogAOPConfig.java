package cn.geelato.web.platform.aop;
import cn.geelato.core.SessionCtx;
import cn.geelato.web.platform.aop.annotation.OpLog;
import com.alibaba.fastjson2.JSONArray;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;

import cn.geelato.core.gql.GqlManager;
import cn.geelato.core.gql.command.CommandType;
import cn.geelato.core.gql.command.SaveCommand;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;

import cn.geelato.core.orm.Dao;
import cn.geelato.utils.UIDGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;


import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

@Aspect
@Component
public class OpLogAOPConfig {

    @Autowired
    @Qualifier("primaryDao")
    private Dao dao;

    private final GqlManager gqlManager = GqlManager.singleInstance();

    @Around(value = "@annotation( cn.geelato.web.platform.aop.annotation.OpLog)")
    public Object around(ProceedingJoinPoint proceedingJoinPoint){
        MethodSignature methodSignature = (MethodSignature)proceedingJoinPoint.getSignature();
        Method method = methodSignature.getMethod();
        OpLog opLog=method.getAnnotation(OpLog.class);
        Object ret= null;
        try {
            ret = proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        switch(opLog.type()){
            case "save":
                resolveSaveOpRecord(proceedingJoinPoint,ret);
            default:
                break;
        }
            return ret;
    }



    private void resolveSaveOpRecord(ProceedingJoinPoint proceedingJoinPoint, Object ret){
        String gql=(String) proceedingJoinPoint.getArgs()[1];
        SaveCommand saveCommand = gqlManager.generateSaveSql(gql, new SessionCtx());
        EntityMeta entityMeta= MetaManager.singleInstance().get(saveCommand.getEntityName());
        String opUser= SessionCtx.getCurrentUser().getUserName();
        String opUserId= SessionCtx.getCurrentUser().getUserId();
        String opDataId="";
        String opType="";
        String opRecord="";
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        if(ret!=null) {
            opDataId=ret.toString();
            if(opDataId.length()>32){
                return;
            }
            if (saveCommand.getCommandType() == CommandType.Update) {
                opType = "u";
                ArrayList<String> filedChangeRecods= new ArrayList<String>();
                for (Map.Entry<String, Object> entry : saveCommand.getValueMap().entrySet()) {
                    String fieldKey = entry.getKey();
                    String fieldName = entityMeta.getFieldMeta(fieldKey).getTitle();
                    String fieldValue=null;
                    if(entry.getValue()!=null){
                        fieldValue = entry.getValue().toString();
                    }
                    if(fieldName!=null&&fieldValue!=null){
                        //todo 如需改为“原值”修改为“目标值”,需多一次数据库，暂定不实现。
                        String filedChangeRecod = String.format("%s修改为%s", fieldName, fieldValue);
                        filedChangeRecods.add(filedChangeRecod);
                    }
                }
                opRecord= JSONArray.toJSONString(filedChangeRecods);
            } else if (saveCommand.getCommandType() == CommandType.Insert) {
                opType = "c";
                opRecord = "新增记录";
            }
            //todo after <use entity to crud without class>
            String baseSql="insert into platform_oprecord (id,op_data_id,op_type,op_time,op_user,op_user_id,op_description,tenant_code) values (?,?,?,?,?,?,?,?)";
            dao.getJdbcTemplate().update(baseSql,
                    UIDGenerator.generate(),
                    opDataId,
                    opType,
                    formatter.format(date),
                    opUser,
                    opUserId,
                    opRecord,
                    SessionCtx.getCurrentTenantCode());
        }

    }

}