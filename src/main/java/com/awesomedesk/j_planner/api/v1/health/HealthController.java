package com.awesomedesk.j_planner.api.v1.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FE가 BE 연결을 확인하는 가장 단순한 엔드포인트 (US-03). DB는 확인하지 않는다.
 */
@RestController
public class HealthController {

    public record HealthResponse(String status) {
    }

    @GetMapping("/api/v1/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
