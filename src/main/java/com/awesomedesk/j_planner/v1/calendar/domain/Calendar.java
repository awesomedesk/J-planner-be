package com.awesomedesk.j_planner.v1.calendar.domain;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import com.awesomedesk.j_planner.common.domain.BaseEntity;
import com.awesomedesk.j_planner.common.domain.DateDto;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "CALENDARS")
public class Calendar extends BaseEntity {

    @Id
    @Column(name = "CALENDAR_ID")
    private Long id;

    private String title;
    private String information;
    private String location;

    @Column(length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean isAllDay;

    @Embedded
    private DateDto dateDto;
}
