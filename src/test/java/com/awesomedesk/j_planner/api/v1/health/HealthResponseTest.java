package com.awesomedesk.j_planner.api.v1.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/** /health의 빌드 정보: 지금 켜진 서버가 어떤 코드인지 (커밋·빌드 시각) — DB 없이 */
class HealthResponseTest {

    private static BuildProperties build(String commit, String commitTime) {
        Properties p = new Properties();
        p.setProperty("version", "1.0-SNAPSHOT");
        p.setProperty("time", "2026-09-28T02:10:00Z");
        if (commit != null) {
            p.setProperty("commit", commit);
        }
        if (commitTime != null) {
            p.setProperty("commitTime", commitTime);
        }
        return new BuildProperties(p);
    }

    @Test
    @DisplayName("버전·커밋·커밋 시각·빌드 시각을 한국 시각으로 보여준다")
    void fromBuildInfo() {
        HealthResponse r = HealthResponse.of(build("87b2945", "2026-09-28T01:58:31Z"));

        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.version()).isEqualTo("1.0-SNAPSHOT");
        assertThat(r.commit()).isEqualTo("87b2945");
        assertThat(r.commitTime()).isEqualTo(LocalDateTime.of(2026, 9, 28, 10, 58, 31));
        assertThat(r.buildTime()).isEqualTo(LocalDateTime.of(2026, 9, 28, 11, 10, 0));
    }

    @Test
    @DisplayName("커밋 시각은 시간대가 붙은 값(+09:00)도 읽는다")
    void commitTimeWithOffset() {
        assertThat(HealthResponse.of(build("87b2945", "2026-09-28T10:58:31+09:00")).commitTime())
            .isEqualTo(LocalDateTime.of(2026, 9, 28, 10, 58, 31));
    }

    @Test
    @DisplayName("git 정보를 못 읽었으면(zip으로 받은 코드 등) 커밋은 null, 서버 상태는 UP")
    void noGit() {
        HealthResponse r = HealthResponse.of(build(null, null));
        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.commit()).isNull();
        assertThat(r.commitTime()).isNull();
        assertThat(r.buildTime()).isNotNull();
    }

    @Test
    @DisplayName("빌드 정보 파일이 없으면(IDE에서 Gradle 없이 실행) 상태만")
    void noBuildInfo() {
        HealthResponse r = HealthResponse.of(null);
        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.version()).isNull();
        assertThat(r.commit()).isNull();
        assertThat(r.buildTime()).isNull();
    }
}
