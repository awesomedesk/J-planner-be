package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.api.v1.auth.PasswordCredentials;
import com.awesomedesk.j_planner.api.v1.auth.google.UserIdentities;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 내 계정 (US-34: 내 정보. loginMethods: EMAIL = 비밀번호 있음, GOOGLE = 구글 연결(US-36). US-37: 이름 변경) */
@Service
@RequiredArgsConstructor
public class MeService {

    private final JdbcTemplate jdbc;
    private final PasswordCredentials passwordCredentials;
    private final UserIdentities identities;
    private final Clock clock;

    static final int NAME_MAX = 50;

    /** 이름 변경 (US-37). 앞뒤 공백 정리, 1~50자(코드 포인트). null이면 그대로 */
    public MeResponse updateName(long userId, String rawName) {
        if (rawName != null) {
            String name = rawName.strip();
            int length = name.codePointCount(0, name.length());
            if (length < 1 || length > NAME_MAX) {
                throw ApiException.validation("name", "이름은 1~" + NAME_MAX + "자로 입력해 주세요.");
            }
            jdbc.update("UPDATE users SET name = ?, updated_at = ? WHERE user_id = ?", name, LocalDateTime.now(clock).withNano(0), userId);
        }
        return get(userId);
    }

    public MeResponse get(long userId) {
        List<MeResponse> found = jdbc.query("SELECT email, name FROM users WHERE user_id = ?", (rs, i) -> {
            boolean hasPassword = passwordCredentials.exists(userId);
            List<LoginMethod> methods = new ArrayList<>();
            if (hasPassword) {
                methods.add(LoginMethod.EMAIL);
            }
            if (identities.hasGoogle(userId)) {
                methods.add(LoginMethod.GOOGLE);
            }
            return new MeResponse(rs.getString("email"), rs.getString("name"), hasPassword, methods);
        }, userId);
        if (found.isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return found.getFirst();
    }
}
