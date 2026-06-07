package com.ufc.server.validate;

import com.ufc.server.dto.PlaceOrderDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LimitPriceValidator
    implements ConstraintValidator<ValidLimitPrice, PlaceOrderDTO>
{

    @Override
    public boolean isValid(PlaceOrderDTO dto, ConstraintValidatorContext ctx) {
        if (dto.type() == null) return true;
        return switch (dto.type()) {
            case LIMIT -> dto.limitPrice() != null;
            case MARKET -> dto.limitPrice() == null;
        };
    }
}
