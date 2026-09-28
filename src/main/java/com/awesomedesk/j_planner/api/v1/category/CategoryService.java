package com.awesomedesk.j_planner.api.v1.category;

import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.Positions;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 카테고리 (US-04, 08-api-design.md 3절)
 * - 미지정: 항상 맨 위, 이름·색 변경·삭제·이동 모두 불가 — D-014, D-029, D-037
 * - 색은 고르지 않으면 null (D-037)
 * - 이름 중복 금지, 새 카테고리는 맨 뒤 — D-029, D-032
 * - 연결 개수: 삭제된 것 제외, 완료한 Todo 포함 — D-032
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;

    public List<CategoryResponse> list() {
        Map<Long, Long> scheduleCounts = toMap(categoryRepository.countSchedulesByCategory());
        Map<Long, Long> todoCounts = toMap(categoryRepository.countTodosByCategory());
        return categoryRepository.findAllOrdered().stream()
            .map(c -> CategoryResponse.of(c, scheduleCounts.getOrDefault(c.getId(), 0L), todoCounts.getOrDefault(c.getId(), 0L)))
            .toList();
    }

    public CategoryResponse get(Long id) {
        return list().stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw duplicated(request.name());
        }
        int sortOrder = categoryRepository.findMaxSortOrder() + 1;
        try {
            Category saved = categoryRepository.save(new Category(request.name(), request.color(), sortOrder));
            return CategoryResponse.of(saved, 0, 0);
        } catch (DataIntegrityViolationException e) {
            throw duplicatedIfNameKey(e, request.name());
        }
    }

    @Transactional
    public CategoryResponse update(Long id, JsonNode patch) {
        Category category = find(id);
        CategoryRequest merged = requestValidator.validate(
            jsonMergePatch.apply(new CategoryRequest(category.getName(), category.getColor()), patch, CategoryRequest.class));

        boolean nameChanged = !merged.name().equals(category.getName());
        boolean colorChanged = !java.util.Objects.equals(merged.color(), category.getColor());
        if (category.isDefault() && (nameChanged || colorChanged)) {
            throw new ApiException(ErrorCode.DEFAULT_CATEGORY_LOCKED, "'미지정' 카테고리는 이름과 색을 바꿀 수 없습니다.");
        }
        if (nameChanged && categoryRepository.existsByNameAndIdNot(merged.name(), id)) {
            throw duplicated(merged.name());
        }
        category.change(merged.name(), merged.color());
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicatedIfNameKey(e, merged.name());
        }
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (category.isDefault()) {
            throw new ApiException(ErrorCode.DEFAULT_CATEGORY_LOCKED, "'미지정' 카테고리는 삭제할 수 없습니다.");
        }
        Long defaultId = categoryRepository.getDefault().getId();
        categoryRepository.moveSchedules(id, defaultId);
        categoryRepository.moveTodos(id, defaultId);
        categoryRepository.delete(categoryRepository.getReferenceById(id));
    }

    @Transactional
    public CategoryResponse move(Long id, Long afterId) {
        Category category = find(id);
        if (category.isDefault()) {
            throw new ApiException(ErrorCode.DEFAULT_CATEGORY_LOCKED, "'미지정' 카테고리는 순서를 바꿀 수 없습니다. 항상 맨 위입니다.");
        }
        Category defaultCategory = categoryRepository.getDefault();
        // 미지정 뒤로 = 맨 앞 (08-api-design.md 2-5절)
        Long normalizedAfterId = defaultCategory.getId().equals(afterId) ? null : afterId;

        List<Category> others = categoryRepository.findAllOrdered().stream().filter(c -> !c.isDefault()).toList();
        Positions.reorder(others, category, normalizedAfterId, 1); // 미지정(0) 다음부터
        categoryRepository.flush();
        return get(id);
    }

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ApiException notFound(Long id) {
        return ApiException.notFound("카테고리를 찾을 수 없습니다: " + id);
    }

    private static ApiException duplicated(String name) {
        return new ApiException(ErrorCode.CATEGORY_NAME_DUPLICATED, "같은 이름의 카테고리가 이미 있습니다: " + name);
    }

    /**
     * 이름 검사와 저장 사이에 다른 요청이 같은 이름을 먼저 저장한 경우 (유니크 키 uk_categories_active_name).
     * 그 밖의 제약 오류는 그대로 올려 공통 처리(409 CONFLICT)에 맡긴다.
     */
    private static RuntimeException duplicatedIfNameKey(DataIntegrityViolationException e, String name) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains("uk_categories_active_name") ? duplicated(name) : e;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }
}
