package com.ufc.server.validate;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LimitPriceValidator.class)
public @interface ValidLimitPrice {
    String message() default "limitPrice is required for LIMIT orders and must be omitted for MARKET orders";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
