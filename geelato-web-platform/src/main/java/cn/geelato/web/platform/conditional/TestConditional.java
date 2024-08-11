package cn.geelato.web.platform.conditional;


import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class TestConditional implements Condition {
    @Override
    public boolean matches(@NotNull ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
        return false;
    }
}
