package com.awesomedesk.j_planner.api.v1.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 24시간이 지난 임시 가입을 매시간 지운다 (US-33 AC3, 07 11-2) */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSignupCleaner {

    private final PendingSignups pendingSignups;
    private final Clock clock;

    /** 매시 7분 (정각 몰림 피함) */
    @Scheduled(cron = "0 7 * * * *", zone = "Asia/Seoul")
    public int deleteExpired() {
        int deleted = pendingSignups.deleteExpired(LocalDateTime.now(clock).withNano(0));
        if (deleted > 0) {
            log.info("만료된 임시 가입 {}건 지움", deleted);
        }
        return deleted;
    }
}
