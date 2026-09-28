package com.awesomedesk.j_planner.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 공통 엔티티 필드(생성·수정·삭제 시각)는 JPA·Auditing만 바꾼다 — 밖에서 바꾸는 setter·내용을 찍는 toString 없음 */
class BaseEntityTest {

    @Test
    @DisplayName("public setter가 없다")
    void noSetters() {
        assertThat(Arrays.stream(BaseEntity.class.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .map(Method::getName))
            .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    @DisplayName("toString을 따로 만들지 않는다 (로그에 엔티티 내용이 찍히지 않게)")
    void noToString() {
        assertThat(Arrays.stream(BaseEntity.class.getDeclaredMethods()).map(Method::getName)).doesNotContain("toString");
    }
}
