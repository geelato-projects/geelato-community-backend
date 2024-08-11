package cn.geelato.web.platform.conditional;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RuntimeServiceConditional  implements Condition {
    @Override
    public boolean matches(@NotNull ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
        //todo 判断该包是否运行时，如运行时，放行运行时接口
        return true;
    }
}
