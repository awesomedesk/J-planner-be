package com.awesomedesk.j_planner.common.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열 최대 글자 수 — 유니코드 글자(code point) 단위로 센다. null은 통과.
 * <p>
 * {@code @Size}는 UTF-16 단위로 세서 이모지 한 글자를 2자로 본다. 08-openapi.yaml의 {@code maxLength}(JSON Schema)는
 * 글자 단위이고 DB(utf8mb4 VARCHAR(n))도 글자 단위라, 사용자에게 보이는 글자 수 제한은 이것을 쓴다 (PO #398).
 */
@Documented
@Constraint(validatedBy = MaxCharsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxChars {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
