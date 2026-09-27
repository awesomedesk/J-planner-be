package com.awesomedesk.j_planner.api.v1.dday;

import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Display;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * D-Day 하나의 달력 표시 계산 (08-api-design.md 6절, D-020·D-032·D-043)
 * <ul>
 *   <li>TARGET: 목표 날짜 (항상)</li>
 *   <li>INTERVAL: 당일=0일은 목표일 전 N의 배수 날(D-N), 당일=1일은 n일째가 N의 배수인 날(100일 = 목표일+99일)</li>
 *   <li>LAST_DAYS: 당일=0일만, D-N ~ D-1</li>
 *   <li>DAILY: 당일=0일만, 등록일 ~ 목표일 전날</li>
 *   <li>YEARLY: 당일=1일만, 목표일의 n년 뒤 (2/29는 평년 2/28)</li>
 * </ul>
 * 같은 날짜에 여러 개가 걸리면 하나만: TARGET &gt; YEARLY &gt; INTERVAL &gt; LAST_DAYS &gt; DAILY
 */
final class DdayMarkCalculator {

    /** 우선순위 순서로 적는다 (앞이 먼저) */
    enum Kind { TARGET, YEARLY, INTERVAL, LAST_DAYS, DAILY }

    record Spec(CountType countType, LocalDate targetDate, Display display, LocalDate registeredOn) {
    }

    record Mark(LocalDate date, String label, Kind kind) {
    }

    private DdayMarkCalculator() {
    }

    /** from ~ to (둘 다 포함) 안에서 표시할 날짜, 날짜 순 */
    static List<Mark> marks(Spec spec, LocalDate from, LocalDate to) {
        List<Mark> marks = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Mark mark = markOn(spec, date);
            if (mark != null) {
                marks.add(mark);
            }
        }
        return marks;
    }

    private static Mark markOn(Spec s, LocalDate date) {
        LocalDate target = s.targetDate();
        Display d = s.display();
        long diff = ChronoUnit.DAYS.between(target, date); // 날짜 - 목표일
        String countLabel = DdayCounter.count(s.countType(), target, date).label();

        if (diff == 0) {
            return new Mark(date, countLabel, Kind.TARGET);
        }
        if (s.countType() == CountType.COUNTUP) {
            if (diff < 0) {
                return null;
            }
            if (d.yearly()) {
                int years = date.getYear() - target.getYear();
                if (years >= 1 && target.plusYears(years).equals(date)) {
                    return new Mark(date, years + "주년", Kind.YEARLY);
                }
            }
            long dayNumber = diff + 1;
            if (d.interval().enabled() && dayNumber % d.interval().days() == 0) {
                return new Mark(date, dayNumber + "일", Kind.INTERVAL);
            }
            return null;
        }

        // COUNTDOWN: 목표일 뒤에는 표시 없음 (D-043)
        if (diff > 0) {
            return null;
        }
        long daysLeft = -diff;
        if (d.interval().enabled() && daysLeft % d.interval().days() == 0) {
            return new Mark(date, countLabel, Kind.INTERVAL);
        }
        if (d.lastDays().enabled() && daysLeft <= d.lastDays().days()) {
            return new Mark(date, countLabel, Kind.LAST_DAYS);
        }
        if (d.daily() && !date.isBefore(s.registeredOn())) {
            return new Mark(date, countLabel, Kind.DAILY);
        }
        return null;
    }
}
