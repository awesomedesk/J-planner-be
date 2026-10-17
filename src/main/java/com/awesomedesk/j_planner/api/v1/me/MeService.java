package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.api.v1.auth.PasswordCredentials;
import com.awesomedesk.j_planner.api.v1.auth.google.UserIdentities;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 내 계정 (US-34: 내 정보. loginMethods: EMAIL = 비밀번호 있음, GOOGLE = 구글 연결(US-36). 이름 변경·탈퇴 등은 US-37) */
@Service
@RequiredArgsConstructor
public class MeService {

    private final JdbcTemplate jdbc;
    private final PasswordCredentials passwordCredentials;
    private final UserIdentities identities;

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
