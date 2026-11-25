package com.awesomedesk.j_planner.common.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link MaxChars} 검사 */
public class MaxCharsValidator implements ConstraintValidator<MaxChars, String> {

    private int max;

    @Override
    public void initialize(MaxChars annotation) {
        max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.codePointCount(0, value.length()) <= max;
    }
}
