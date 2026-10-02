package com.dayan.platform.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String module();

    String action();

    String targetType();

    String targetId() default "";

    String operatorId() default "";

    String operatorName() default "";
}
