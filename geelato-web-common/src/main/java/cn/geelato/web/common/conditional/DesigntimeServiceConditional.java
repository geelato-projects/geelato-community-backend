package cn.geelato.web.common.conditional;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DesigntimeServiceConditional  implements Condition {

    private static final String LEGACY_PROPERTY = "geelato.web";
    private static final String DESIGN_TIME_PROPERTY = "geelato.web.platform.design-time.enabled";

    @Override
    public boolean matches(@NotNull ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
        String designTimeEnabled = context.getEnvironment().getProperty(DESIGN_TIME_PROPERTY);
        if (designTimeEnabled != null) {
            return Boolean.parseBoolean(designTimeEnabled);
        }

        // Keep backward compatibility with the historical switch.
        String webOption = context.getEnvironment().getProperty(LEGACY_PROPERTY);
        if (webOption != null) {
            return Boolean.parseBoolean(webOption);
        }

        return true;
    }
}
