package com.awesomedesk.j_planner.common.api;

import static com.awesomedesk.j_planner.support.ApiExceptionAssertions.assertErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** PATCH = JSON Merge Patch (RFC 7396, 08-api-design.md 2-4절) — DB 없이 */
class JsonMergePatchTest {

    record Place(String name, Double latitude) {
    }

    record Item(String title, String color, Integer count, Place place) {
    }

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonMergePatch patch = new JsonMergePatch(mapper);
    private final Item current = new Item("회의", "#2F62A8", 3, new Place("강남역", 37.5));

    private Item apply(String json) {
        return patch.apply(current, mapper.readTree(json), Item.class);
    }

    @Test
    @DisplayName("보내지 않은 필드는 그대로, 보낸 필드만 바뀐다")
    void onlySentFields() {
        assertThat(apply("{\"title\":\"주간 회의\"}"))
            .isEqualTo(new Item("주간 회의", "#2F62A8", 3, new Place("강남역", 37.5)));
    }

    @Test
    @DisplayName("null을 보내면 값을 지운다")
    void nullClears() {
        assertThat(apply("{\"color\":null,\"place\":null}")).isEqualTo(new Item("회의", null, 3, null));
    }

    @Test
    @DisplayName("객체는 안쪽 필드도 같은 규칙으로 합친다")
    void nestedMerge() {
        assertThat(apply("{\"place\":{\"name\":\"역삼역\"}}").place()).isEqualTo(new Place("역삼역", 37.5));
        assertThat(apply("{\"place\":{\"latitude\":null}}").place()).isEqualTo(new Place("강남역", null));
    }

    @Test
    @DisplayName("빈 객체는 아무것도 바꾸지 않는다")
    void emptyPatch() {
        assertThat(apply("{}")).isEqualTo(current);
    }

    @Test
    @DisplayName("형식이 틀린 값은 그 필드 이름으로 알려준다 (안쪽 필드는 점으로)")
    void invalidFieldName() {
        ApiException e = catchThrowableOfType(ApiException.class, () -> apply("{\"count\":\"많이\"}"));
        assertThat(e.getErrors()).extracting("field").containsExactly("count");
        ApiException nested = catchThrowableOfType(ApiException.class, () -> apply("{\"place\":{\"latitude\":\"북쪽\"}}"));
        assertThat(nested.getErrors()).extracting("field").containsExactly("place.latitude");
    }

    @Test
    @DisplayName("객체가 아닌 본문·형식이 틀린 값 → VALIDATION_FAILED")
    void invalid() {
        assertErrorCode(() -> apply("[1,2]"), ErrorCode.VALIDATION_FAILED);
        assertErrorCode(() -> apply("{\"count\":\"많이\"}"), ErrorCode.VALIDATION_FAILED);
    }
}
