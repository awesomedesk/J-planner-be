package com.awesomedesk.j_planner.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 컨트롤러 밖(Spring Security 필터: 로그인 안 함 401, CSRF 403)에서 {@link GlobalExceptionHandler}와 같은 모양의
 * Problem Details를 쓴다 (08-api-design.md 2-6절).
 */
@Component
@RequiredArgsConstructor
public class ProblemWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", code.getStatus().getReasonPhrase());
        body.put("status", code.getStatus().value());
        body.put("detail", code.getDefaultDetail());
        body.put("instance", request.getRequestURI());
        body.put("code", code.name());
        body.put("errors", List.of());
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
