package com.awesomedesk.j_planner.api.v1.memo;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertFieldError;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 메모 제목·내용 정리와 빈 메모 규칙 (D-032) — DB 없이 */
class MemoRulesTest {

    @Test
    @DisplayName("제목은 앞뒤 공백을 지우고, 비면 null. 내용은 그대로 두되 공백뿐이면 null")
    void normalize() {
        assertThat(MemoRules.normalize(" 읽을 책 ", "  - 데미안\n")).isEqualTo(new MemoRules.Normalized("읽을 책", "  - 데미안\n"));
        assertThat(MemoRules.normalize("  ", "내용")).isEqualTo(new MemoRules.Normalized(null, "내용"));
        assertThat(MemoRules.normalize("제목", " \n ")).isEqualTo(new MemoRules.Normalized("제목", null));
    }

    @Test
    @DisplayName("제목·내용이 둘 다 비면 오류 (빈 문자열·공백만 포함)")
    void empty() {
        assertFieldError(() -> MemoRules.normalize(null, null), "title");
        assertFieldError(() -> MemoRules.normalize(" ", "\n"), "title");
    }
}
