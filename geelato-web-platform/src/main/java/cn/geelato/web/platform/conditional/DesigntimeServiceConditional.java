package cn.geelato.web.platform.conditional;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DesigntimeServiceConditional  implements Condition {

    @Override
    public boolean matches(@NotNull ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
        //todo 判断该包是否设计时，如设计时，放行设计时接口
        String webOption=context.getEnvironment().getProperty("geelato.web");
        if (webOption != null) {
            return webOption.equals("true");
        }
        return true;
    }
}
