package com.awesomedesk.j_planner.api.v1.health;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.boot.info.BuildProperties;

/**
 * 서버 상태와 지금 켜진 서버의 코드 정보 (08-api-design.md 2-7절).
 * 시각은 다른 API와 같이 한국 시각, 시간대 없이. 빌드 정보·git 정보가 없으면 그 값은 null.
 *
 * @param version    build.gradle의 version
 * @param commit     빌드할 때의 git 마지막 커밋 (짧은 해시)
 * @param commitTime 그 커밋 시각
 * @param buildTime  빌드한 시각
 */
public record HealthResponse(String status, String version, String commit, LocalDateTime commitTime,
                             LocalDateTime buildTime) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static HealthResponse of(BuildProperties build) {
        if (build == null) {
            return new HealthResponse("UP", null, null, null, null);
        }
        LocalDateTime buildTime = build.getTime() == null ? null : LocalDateTime.ofInstant(build.getTime(), SEOUL);
        return new HealthResponse("UP", build.getVersion(), build.get("commit"), toSeoul(build.get("commitTime")),
            buildTime);
    }

    private static LocalDateTime toSeoul(String isoWithOffset) {
        if (isoWithOffset == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(isoWithOffset).atZoneSameInstant(SEOUL).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
