package com.awesomedesk.j_planner.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class Scheduler {

    @Scheduled(cron="0 0 0 * * *", zone = "Asia/Seoul")
//    @Transactional
    public void inactiveMemberScheduled() {
        log.info("start member inactive");
    }
}
