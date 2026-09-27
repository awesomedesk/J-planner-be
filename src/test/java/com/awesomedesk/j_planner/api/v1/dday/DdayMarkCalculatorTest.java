package com.awesomedesk.j_planner.api.v1.dday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.awesomedesk.j_planner.api.v1.dday.DdayMarkCalculator.Kind;
import com.awesomedesk.j_planner.api.v1.dday.DdayMarkCalculator.Mark;
import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Display;
import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Option;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** D-Day 달력 표시 계산 (08-api-design.md 6절, D-020·032·043) — DB 없이 */
class DdayMarkCalculatorTest {

    private static final Display NONE = new Display(new Option(false, 100), new Option(false, 7), false, false);

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static List<Mark> marks(CountType type, String target, Display display, String registered, String from, String to) {
        return DdayMarkCalculator.marks(new DdayMarkCalculator.Spec(type, d(target), display, d(registered)), d(from), d(to));
    }

    @Nested
    @DisplayName("목표 날짜 (TARGET)")
    class Target {
        @Test
        @DisplayName("옵션이 없어도 목표 날짜 칸에는 항상. 당일=0일 D-Day, 당일=1일 D+1")
        void always() {
            assertThat(marks(CountType.COUNTDOWN, "2026-10-01", NONE, "2026-09-01", "2026-09-01", "2026-10-31"))
                .extracting(Mark::date, Mark::label, Mark::kind)
                .containsExactly(tuple(d("2026-10-01"), "D-Day", Kind.TARGET));
            assertThat(marks(CountType.COUNTUP, "2026-10-01", NONE, "2026-09-01", "2026-09-01", "2026-10-31"))
                .extracting(Mark::label).containsExactly("D+1");
        }

        @Test
        @DisplayName("범위 밖이면 없음")
        void outOfRange() {
            assertThat(marks(CountType.COUNTDOWN, "2026-12-31", NONE, "2026-09-01", "2026-09-01", "2026-10-31")).isEmpty();
        }
    }

    @Nested
    @DisplayName("N일 단위 (INTERVAL)")
    class Interval {
        @Test
        @DisplayName("당일=0일: 목표일 전 N의 배수 날 D-N (D-043: 목표일 뒤 D+n 없음)")
        void countdown() {
            Display every30 = new Display(new Option(true, 30), new Option(false, 7), false, false);
            assertThat(marks(CountType.COUNTDOWN, "2026-10-31", every30, "2026-01-01", "2026-09-01", "2026-10-31"))
                .extracting(Mark::date, Mark::label, Mark::kind)
                .containsExactly(
                    tuple(d("2026-09-01"), "D-60", Kind.INTERVAL),
                    tuple(d("2026-10-01"), "D-30", Kind.INTERVAL),
                    tuple(d("2026-10-31"), "D-Day", Kind.TARGET));
            assertThat(marks(CountType.COUNTDOWN, "2026-08-01", every30, "2026-01-01", "2026-08-02", "2026-10-01")).isEmpty();
        }

        @Test
        @DisplayName("당일=1일: 목표일이 1일째라 100일 = 목표일 + 99일 (D-043: 7/1 → 10/8)")
        void countup() {
            Display every100 = new Display(new Option(true, 100), new Option(false, 7), false, false);
            assertThat(marks(CountType.COUNTUP, "2026-07-01", every100, "2026-07-01", "2026-09-01", "2026-10-31"))
                .extracting(Mark::date, Mark::label, Mark::kind)
                .containsExactly(tuple(d("2026-10-08"), "100일", Kind.INTERVAL));
            assertThat(marks(CountType.COUNTUP, "2026-07-01", every100, "2026-07-01", "2027-01-01", "2027-01-31"))
                .extracting(Mark::date, Mark::label)
                .containsExactly(tuple(d("2027-01-16"), "200일"));
        }
    }

    @Nested
    @DisplayName("마지막 N일 (LAST_DAYS)")
    class LastDays {
        @Test
        @DisplayName("D-7 ~ D-1 매일 + 목표일은 D-Day (D-043)")
        void lastSeven() {
            Display last7 = new Display(new Option(false, 100), new Option(true, 7), false, false);
            assertThat(marks(CountType.COUNTDOWN, "2026-10-10", last7, "2026-01-01", "2026-10-01", "2026-10-12"))
                .extracting(Mark::label)
                .containsExactly("D-7", "D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "D-Day");
        }
    }

    @Nested
    @DisplayName("매일 (DAILY)")
    class Daily {
        @Test
        @DisplayName("등록일 ~ 목표일 전날 매일, 목표일은 D-Day, 그 뒤 없음 (D-043)")
        void fromRegistered() {
            Display daily = new Display(new Option(false, 100), new Option(false, 7), true, false);
            assertThat(marks(CountType.COUNTDOWN, "2026-09-30", daily, "2026-09-27", "2026-09-20", "2026-10-05"))
                .extracting(Mark::date, Mark::label)
                .containsExactly(
                    tuple(d("2026-09-27"), "D-3"),
                    tuple(d("2026-09-28"), "D-2"),
                    tuple(d("2026-09-29"), "D-1"),
                    tuple(d("2026-09-30"), "D-Day"));
        }

        @Test
        @DisplayName("목표일 뒤에 등록했으면 매일 표시 없음")
        void registeredAfterTarget() {
            Display daily = new Display(new Option(false, 100), new Option(false, 7), true, false);
            assertThat(marks(CountType.COUNTDOWN, "2026-09-10", daily, "2026-09-20", "2026-09-01", "2026-09-30"))
                .extracting(Mark::kind).containsExactly(Kind.TARGET);
        }
    }

    @Nested
    @DisplayName("매년 (YEARLY)")
    class Yearly {
        @Test
        @DisplayName("목표일의 n년 뒤 같은 날 n주년")
        void anniversaries() {
            Display yearly = new Display(new Option(false, 100), new Option(false, 7), false, true);
            assertThat(marks(CountType.COUNTUP, "2024-09-25", yearly, "2024-09-25", "2026-09-01", "2026-10-31"))
                .extracting(Mark::date, Mark::label, Mark::kind)
                .containsExactly(tuple(d("2026-09-25"), "2주년", Kind.YEARLY));
        }

        @Test
        @DisplayName("2월 29일 목표일은 평년에 2월 28일 (D-043)")
        void leapDay() {
            Display yearly = new Display(new Option(false, 100), new Option(false, 7), false, true);
            assertThat(marks(CountType.COUNTUP, "2024-02-29", yearly, "2024-02-29", "2027-02-01", "2027-03-31"))
                .extracting(Mark::date, Mark::label)
                .containsExactly(tuple(d("2027-02-28"), "3주년"));
            assertThat(marks(CountType.COUNTUP, "2024-02-29", yearly, "2024-02-29", "2028-02-01", "2028-03-31"))
                .extracting(Mark::date, Mark::label)
                .containsExactly(tuple(d("2028-02-29"), "4주년"));
        }
    }

    @Nested
    @DisplayName("겹칠 때 하나만: 목표일 > 매년 > N일 단위 > 마지막 N일 > 매일 (D-032)")
    class Priority {
        @Test
        @DisplayName("당일=0일: N일 단위가 마지막 N일·매일보다 먼저, 목표일이 가장 먼저")
        void countdown() {
            Display all = new Display(new Option(true, 5), new Option(true, 7), true, false);
            assertThat(marks(CountType.COUNTDOWN, "2026-10-10", all, "2026-09-28", "2026-09-25", "2026-10-15"))
                .extracting(Mark::date, Mark::kind)
                .containsExactly(
                    tuple(d("2026-09-28"), Kind.DAILY),
                    tuple(d("2026-09-29"), Kind.DAILY),
                    tuple(d("2026-09-30"), Kind.INTERVAL),
                    tuple(d("2026-10-01"), Kind.DAILY),
                    tuple(d("2026-10-02"), Kind.DAILY),
                    tuple(d("2026-10-03"), Kind.LAST_DAYS),
                    tuple(d("2026-10-04"), Kind.LAST_DAYS),
                    tuple(d("2026-10-05"), Kind.INTERVAL),
                    tuple(d("2026-10-06"), Kind.LAST_DAYS),
                    tuple(d("2026-10-07"), Kind.LAST_DAYS),
                    tuple(d("2026-10-08"), Kind.LAST_DAYS),
                    tuple(d("2026-10-09"), Kind.LAST_DAYS),
                    tuple(d("2026-10-10"), Kind.TARGET));
        }

        @Test
        @DisplayName("당일=1일: 매년이 N일 단위보다 먼저, 목표일이 N일 단위보다 먼저")
        void countup() {
            // 2025-09-25 시작 → 2026-09-25 = 366일째 = 1주년
            Display every366 = new Display(new Option(true, 366), new Option(false, 7), false, true);
            assertThat(marks(CountType.COUNTUP, "2025-09-25", every366, "2025-09-25", "2026-09-01", "2026-09-30"))
                .extracting(Mark::date, Mark::label, Mark::kind)
                .containsExactly(tuple(d("2026-09-25"), "1주년", Kind.YEARLY));
            Display everyDay = new Display(new Option(true, 1), new Option(false, 7), false, false);
            assertThat(marks(CountType.COUNTUP, "2026-09-25", everyDay, "2026-09-25", "2026-09-25", "2026-09-26"))
                .extracting(Mark::label, Mark::kind)
                .containsExactly(tuple("D+1", Kind.TARGET), tuple("2일", Kind.INTERVAL));
        }
    }
}
