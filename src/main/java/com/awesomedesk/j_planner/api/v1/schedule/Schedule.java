package com.awesomedesk.j_planner.api.v1.schedule;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import com.awesomedesk.j_planner.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SecondaryRow;
import org.locationtech.jts.geom.Point;

/**
 * 일정 (07-db-design.md 4-2·4-3). DB는 calendars + calendar_details(1:1) 두 테이블이지만 API에서는 하나의 리소스.
 * calendar_details는 보조 테이블로 매핑한다. 일정 1개에 상세 행이 항상 1개 있다 (값이 모두 비어도 행을 만든다).
 * 삭제는 두 테이블 모두 soft delete.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "calendars")
@SecondaryTable(name = "calendar_details", pkJoinColumns = @PrimaryKeyJoinColumn(name = "calendar_id"))
@SecondaryRow(table = "calendar_details", optional = false)
@SQLDelete(sql = "UPDATE calendars SET deleted = 'Y', deleted_at = NOW() WHERE calendar_id = ?")
@SQLDelete(table = "calendar_details",
    sql = "UPDATE calendar_details SET deleted = 'Y', deleted_at = NOW() WHERE calendar_id = ?")
@SQLRestriction("deleted = 'N'")
public class Schedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id")
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Column(name = "all_day", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean allDay;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    /** null = 테마의 Theme2 (D-030) */
    @Column(length = 8)
    private String color;

    @Column(table = "calendar_details", name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(table = "calendar_details", name = "text_location", columnDefinition = "TEXT")
    private String locationName;

    /** MySQL POINT: x = 경도, y = 위도 */
    @Column(table = "calendar_details", name = "location", columnDefinition = "POINT")
    private Point locationPoint;

    @Column(table = "calendar_details", name = "url", length = 2048)
    private String url;

    public Schedule(Values v) {
        apply(v);
    }

    public void apply(Values v) {
        this.categoryId = v.categoryId();
        this.title = v.title();
        this.allDay = v.allDay();
        this.startDateTime = v.start();
        this.endDateTime = v.end();
        this.color = v.color();
        this.description = v.description();
        this.locationName = v.locationName();
        this.locationPoint = v.locationPoint();
        this.url = v.url();
    }

    /** 검증·정리가 끝난 값 */
    public record Values(Long categoryId, String title, boolean allDay, LocalDateTime start, LocalDateTime end,
                         String color, String description, String locationName, Point locationPoint, String url) {
    }
}
