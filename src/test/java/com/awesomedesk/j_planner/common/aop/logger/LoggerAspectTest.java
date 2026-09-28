package com.awesomedesk.j_planner.common.aop.logger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 요청 로그에 일기·메모 같은 본문 내용이 찍히지 않는다 — 값 대신 모양만 */
class LoggerAspectTest {

    record DiaryRequest(String content) {
    }

    record MemoResponse(Long id, String title, String content) {
    }

    @Test
    @DisplayName("id·날짜·짧은 조회 값은 그대로")
    void simpleValues() {
        assertThat(LoggerAspect.describe(12L)).isEqualTo("12");
        assertThat(LoggerAspect.describe(LocalDate.of(2026, 9, 25))).isEqualTo("2026-09-25");
        assertThat(LoggerAspect.describe(true)).isEqualTo("true");
        assertThat(LoggerAspect.describe(null)).isEqualTo("null");
    }

    @Test
    @DisplayName("요청·응답 객체는 타입 이름만 (내용 없음)")
    void objectsHideContent() {
        assertThat(LoggerAspect.describe(new DiaryRequest("오늘은 비밀 이야기"))).isEqualTo("<DiaryRequest>");
        assertThat(LoggerAspect.describe(new MemoResponse(1L, "t", "비밀"))).isEqualTo("<MemoResponse>");
    }

    @Test
    @DisplayName("JSON 본문(PATCH)은 보낸 필드 이름만")
    void jsonFieldNames() {
        var node = JsonMapper.builder().build().readTree("{\"content\":\"비밀\",\"title\":\"t\"}");
        assertThat(LoggerAspect.describe(node)).isEqualTo("JSON[content, title]");
    }

    @Test
    @DisplayName("목록은 개수만")
    void lists() {
        assertThat(LoggerAspect.describe(List.of(1, 2, 3))).isEqualTo("List(size=3)");
    }
}
