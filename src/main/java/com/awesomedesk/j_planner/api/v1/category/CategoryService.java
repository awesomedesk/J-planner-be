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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 카테고리 (US-04, 08-api-design.md 3절)
 * - 미지정: 항상 맨 위, 색만 변경 가능 (이름 변경·삭제·이동 불가) — D-014, D-029
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
        Category saved = categoryRepository.save(new Category(request.name(), request.color(), sortOrder));
        return CategoryResponse.of(saved, 0, 0);
    }

    @Transactional
    public CategoryResponse update(Long id, JsonNode patch) {
        Category category = find(id);
        CategoryRequest merged = requestValidator.validate(
            jsonMergePatch.apply(new CategoryRequest(category.getName(), category.getColor()), patch, CategoryRequest.class));

        boolean nameChanged = !merged.name().equals(category.getName());
        if (nameChanged && category.isDefault()) {
            throw new ApiException(ErrorCode.DEFAULT_CATEGORY_LOCKED, "'미지정' 카테고리는 이름을 바꿀 수 없습니다.");
        }
        if (nameChanged && categoryRepository.existsByNameAndIdNot(merged.name(), id)) {
            throw duplicated(merged.name());
        }
        category.change(merged.name(), merged.color());
        categoryRepository.flush();
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
        List<Category> reordered = Positions.move(others, category, normalizedAfterId, Category::getId);
        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).changeSortOrder(i + 1);
        }
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

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }
}
