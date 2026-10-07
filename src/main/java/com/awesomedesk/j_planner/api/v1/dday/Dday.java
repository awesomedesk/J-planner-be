package com.awesomedesk.j_planner.api.v1.dday;

import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Display;
import com.awesomedesk.j_planner.api.v1.dday.DdayRequest.Option;
import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import com.awesomedesk.j_planner.common.domain.BaseEntity;
import com.awesomedesk.j_planner.common.domain.Sortable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** D-Day (07-db-design.md 4-5) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ddays")
@SQLDelete(sql = "UPDATE ddays SET deleted = 'Y', deleted_at = NOW() WHERE dday_id = ?")
@SQLRestriction("deleted = 'N'")
public class Dday extends BaseEntity implements Sortable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dday_id")
    private Long id;

    /** 회원번호 (US-32). 만든 뒤 바뀌지 않는다 */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false, length = 10)
    private CountType countType;

    @Column(name = "show_interval", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean showInterval;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "show_last_days", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean showLastDays;

    @Column(name = "last_days", nullable = false)
    private int lastDays;

    @Column(name = "show_daily", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean showDaily;

    @Column(name = "show_yearly", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean showYearly;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Dday(Long userId, Values v, int sortOrder) {
        this.userId = userId;
        apply(v);
        this.sortOrder = sortOrder;
    }

    public void apply(Values v) {
        this.title = v.title();
        this.targetDate = v.targetDate();
        this.countType = v.countType();
        Display d = v.display();
        this.showInterval = d.interval().enabled();
        this.intervalDays = d.interval().days();
        this.showLastDays = d.lastDays().enabled();
        this.lastDays = d.lastDays().days();
        this.showDaily = d.daily();
        this.showYearly = d.yearly();
    }

    @Override
    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Display display() {
        return new Display(new Option(showInterval, intervalDays), new Option(showLastDays, lastDays), showDaily, showYearly);
    }

    /** 검증·정리가 끝난 값 (display는 빈 곳 없이 채워진 상태) */
    public record Values(String title, LocalDate targetDate, CountType countType, Display display) {
    }
}
