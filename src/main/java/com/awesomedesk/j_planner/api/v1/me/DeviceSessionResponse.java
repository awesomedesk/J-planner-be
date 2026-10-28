package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.auth.UserAgents;
import java.time.LocalDateTime;

/**
 * 로그인 기기 {@code GET /me/sessions} (08 13-4). id는 세션 ID가 아니다 (지울 때 쓰는 기기 번호).
 * device = 'Chrome · macOS', ipPrefix = IP 앞 두 자리, current = 지금 이 기기
 */
public record DeviceSessionResponse(String id, String device, LocalDateTime loggedInAt, LocalDateTime lastUsedAt, String ipPrefix, boolean current) {

    static DeviceSessionResponse from(DeviceSessions.Device d) {
        return new DeviceSessionResponse(d.id(), UserAgents.describe(d.userAgent()), d.loggedInAt() == null ? d.lastUsedAt() : d.loggedInAt(),
            d.lastUsedAt(), UserAgents.ipPrefix(d.ip()), d.current());
    }
}
