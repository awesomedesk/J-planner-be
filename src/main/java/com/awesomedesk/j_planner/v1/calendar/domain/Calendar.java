package com.awesomedesk.j_planner.v1.calendar.domain;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import com.awesomedesk.j_planner.common.domain.BaseEntity;
import com.awesomedesk.j_planner.common.domain.DateDto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@ToString(exclude = {"detail"})
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "calendars")
public class Calendar extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "all_day", length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean allDay;

    @Embedded
    private DateDto dateDto;

    private String color;

    @OneToOne(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true)
    private CalendarDetail detail;

    public void update(String title, String color, boolean allDay, LocalDateTime startDatetime, LocalDateTime endDatetime) {
        this.title = title;
        this.color = color;
        this.allDay = allDay;
        if (this.dateDto == null) {
            this.dateDto = new DateDto();
        }
        this.dateDto.setStartDateTime(startDatetime);
        this.dateDto.setEndDateTime(endDatetime);
    }

    public void delete() {
        this.setDeleted(true);
        this.setDeletedAt(LocalDateTime.now());
        if (this.detail != null) {
            this.detail.delete();
        }
    }

    public void setDetail(CalendarDetail detail) {
        this.detail = detail;
        if (detail != null) {
            detail.setCalendar(this);
        }
    }

}
