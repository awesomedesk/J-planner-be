package com.awesomedesk.j_planner.acceptance;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-25 사용자는 날짜와 상관없는 메모를 쓴다 (09-backlog.md)
 * <ul>
 *   <li>AC2 저장 버튼·자동 저장 없음·검색 없음 → FE 담당 (서버는 요청이 올 때만 저장, 검색 조건 없음)</li>
 *   <li>AC3 중 "새 메모를 비운 채 뒤로 가면 요청 없이 버림" → FE 담당. 빈 메모 저장 거절만 여기서</li>
 * </ul>
 */
@DisplayName("US-25 메모")
class US25MemoAcceptanceTest extends AcceptanceTest {

    private static final String URL = "/api/v1/memos";

    private long createMemo(String title, String content) throws Exception {
        return idOf(postJson(URL, "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}").andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("AC1: 제목 + 내용, 날짜와 연결되지 않는다 (D-011)")
    void titleAndContent() throws Exception {
        long id = createMemo("읽을 책", "- 데미안\\n- 코스모스");

        getJson(URL + "/" + id)
            .andExpect(jsonPath("$.title").value("읽을 책"))
            .andExpect(jsonPath("$.content").value("- 데미안\n- 코스모스"))
            .andExpect(jsonPath("$.createdAt").value("2026-09-25T09:00:00"))
            .andExpect(jsonPath("$.date").doesNotExist());
    }

    @Test
    @DisplayName("AC1: 목록은 최근 수정 순 (D-029)")
    void recentlyUpdatedFirst() throws Exception {
        long a = createMemo("A", "a");
        clock.advance(Duration.ofMinutes(1));
        createMemo("B", "b");
        clock.advance(Duration.ofMinutes(1));
        createMemo("C", "c");
        getJson(URL).andExpect(jsonPath("$[*].title", contains("C", "B", "A")));

        clock.advance(Duration.ofMinutes(1));
        patchJson(URL + "/" + a, "{\"content\":\"a 고침\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").value("2026-09-25T09:03:00"));
        getJson(URL).andExpect(jsonPath("$[*].title", contains("A", "C", "B")));
    }

    @Test
    @DisplayName("AC2: 저장한 내용만 바뀐다 (보낸 필드만 수정)")
    void saveOnlySent() throws Exception {
        long id = createMemo("장보기", "우유");
        patchJson(URL + "/" + id, "{\"content\":\"우유, 계란\"}")
            .andExpect(jsonPath("$.title").value("장보기"))
            .andExpect(jsonPath("$.content").value("우유, 계란"));
    }

    @Test
    @DisplayName("AC3: 제목·내용 중 하나만 있어도 되고, 둘 다 비면 저장 불가 (추가·수정 모두, D-032)")
    void emptyMemoRejected() throws Exception {
        postJson(URL, "{\"title\":\"제목만\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.content").value(nullValue()));
        postJson(URL, "{\"content\":\"내용만\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(nullValue()));

        postJson(URL, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postJson(URL, "{\"title\":\"  \",\"content\":\"\\n \"}").andExpect(status().isBadRequest());

        long id = createMemo("장보기", "우유");
        patchJson(URL + "/" + id, "{\"title\":null,\"content\":\"\"}").andExpect(status().isBadRequest());
        getJson(URL + "/" + id).andExpect(jsonPath("$.title").value("장보기"));
        getJson(URL).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("삭제하면 목록에서 사라진다 (MEMO-03)")
    void delete() throws Exception {
        long id = createMemo("A", "a");
        deleteJson(URL + "/" + id).andExpect(status().isNoContent());
        getJson(URL + "/" + id).andExpect(status().isNotFound());
        getJson(URL).andExpect(jsonPath("$.length()").value(0));
    }
}
