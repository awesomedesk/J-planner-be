package com.awesomedesk.j_planner.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트용 시계. 기본은 고정 시각이고, {@link #advance}로 시간을 흘려 "나중에 수정" 같은 상황을 만든다 */
public class TestClock extends Clock {

    private final Instant start;
    private final ZoneId zone;
    private volatile Instant now;

    public TestClock(Instant start, ZoneId zone) {
        this.start = start;
        this.zone = zone;
        this.now = start;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void reset() {
        now = start;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
