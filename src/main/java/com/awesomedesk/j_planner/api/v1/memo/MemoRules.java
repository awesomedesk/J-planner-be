package com.awesomedesk.j_planner.api.v1.memo;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.FieldErrorDetail;
import java.util.List;

/**
 * 메모 제목·내용 정리와 빈 메모 규칙 (08-api-design.md 8절, D-032)
 * <ul>
 *   <li>제목: 앞뒤 공백 제거, 비면 null</li>
 *   <li>내용: 그대로 두되 공백뿐이면 null</li>
 *   <li>둘 다 비면 400 (errors에 title·content 둘 다)</li>
 * </ul>
 */
final class MemoRules {

    record Normalized(String title, String content) {
    }

    private MemoRules() {
    }

    static Normalized normalize(String title, String content) {
        String t = title == null || title.isBlank() ? null : title.strip();
        String c = content == null || content.isBlank() ? null : content;
        if (t == null && c == null) {
            String message = "제목이나 내용 중 하나는 입력하세요.";
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultDetail(),
                List.of(new FieldErrorDetail("title", message), new FieldErrorDetail("content", message)));
        }
        return new Normalized(t, c);
    }
}
