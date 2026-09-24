package com.awesomedesk.j_planner.common.api;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PATCH 본문을 JSON Merge Patch(RFC 7396) 규칙으로 적용한다 (08-api-design.md 2-4절).
 * <ul>
 *   <li>보내지 않은 필드 → 그대로</li>
 *   <li>{@code null} → 값을 지운다</li>
 *   <li>객체 → 안쪽 필드도 같은 규칙으로 적용</li>
 * </ul>
 * 사용: 현재 값을 요청 DTO로 만든 뒤 {@code apply(current, patch, Dto.class)} → 합친 결과를 검증 → 엔티티에 반영.
 */
@Component
public class JsonMergePatch {

    private final JsonMapper jsonMapper;

    public JsonMergePatch(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> T apply(T current, JsonNode patch, Class<T> type) {
        if (patch == null || !patch.isObject()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "수정할 내용을 JSON 객체로 보내세요.");
        }
        ObjectNode target = jsonMapper.valueToTree(current);
        merge(target, (ObjectNode) patch);
        try {
            return jsonMapper.treeToValue(target, type);
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "입력값의 형식이 올바르지 않습니다.");
        }
    }

    private static void merge(ObjectNode target, ObjectNode patch) {
        for (Map.Entry<String, JsonNode> entry : patch.properties()) {
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull()) {
                target.putNull(name);
            } else if (value.isObject() && target.get(name) instanceof ObjectNode existing) {
                merge(existing, (ObjectNode) value);
            } else {
                target.set(name, value);
            }
        }
    }
}
