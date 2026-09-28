package com.awesomedesk.j_planner.common.error;

import java.util.List;
import tools.jackson.core.JacksonException;

/**
 * JSON을 읽다 실패한 위치를 오류 응답의 필드 이름으로 바꾼다.
 * 예) place.latitude, sidebarItems[0].type — Bean Validation 오류와 같은 모양
 */
public final class JsonFieldPath {

    private JsonFieldPath() {
    }

    /** 위치를 모르면 null */
    public static String of(JacksonException e) {
        List<JacksonException.Reference> path = e.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : path) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** 필드 오류 목록 (위치를 모르면 빈 목록) */
    public static List<FieldErrorDetail> errors(JacksonException e) {
        String field = of(e);
        return field == null ? List.of() : List.of(new FieldErrorDetail(field, "형식이 올바르지 않거나 허용되지 않는 값입니다."));
    }
}
