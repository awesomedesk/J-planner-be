package com.awesomedesk.j_planner.api.v1.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FE가 BE 연결을 확인하는 가장 단순한 엔드포인트 (US-03). DB는 확인하지 않는다.
 * 지금 켜진 서버가 어떤 코드인지(버전·커밋·빌드 시각)도 함께 보여준다.
 */
@RestController
public class HealthController {

    private final HealthResponse response;

    /** 빌드 정보(META-INF/build-info.properties)는 Gradle 빌드 때 만들어진다. 없으면 상태만 */
    public HealthController(ObjectProvider<BuildProperties> buildProperties) {
        this.response = HealthResponse.of(buildProperties.getIfAvailable());
    }

    @GetMapping("/api/v1/health")
    public HealthResponse health() {
        return response;
    }
}
