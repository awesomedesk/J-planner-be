package com.awesomedesk.j_planner.api.v1.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 카테고리 이름 중복 검사와 DB 저장 사이에 같은 이름이 먼저 저장된 경우 (동시 요청) — DB 없이 */
class CategoryServiceTest {

    private static final Long USER = 1L;
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository, mock(JsonMergePatch.class), mock(RequestValidator.class));

    @Test
    @DisplayName("검사 땐 없었는데 저장할 때 같은 이름이 있으면(유니크 키 uk_categories_active_name) → 409 CATEGORY_NAME_DUPLICATED")
    void raceOnCreate() {
        when(repository.existsByUserIdAndName(USER, "공부")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException(
            "Duplicate entry '1-공부' for key 'categories.uk_categories_active_name'"));

        ApiException e = catchThrowableOfType(ApiException.class, () -> service.create(USER, new CategoryRequest("공부", null)));

        assertThat(e).isNotNull();
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("이름 중복이 아닌 다른 제약 오류는 그대로 올린다 (공통 처리 409 CONFLICT)")
    void otherConstraint() {
        when(repository.existsByUserIdAndName(USER, "공부")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("Check constraint 'x' is violated."));

        Throwable t = catchThrowableOfType(DataIntegrityViolationException.class,
            () -> service.create(USER, new CategoryRequest("공부", null)));
        assertThat(t).isNotNull();
    }
}
