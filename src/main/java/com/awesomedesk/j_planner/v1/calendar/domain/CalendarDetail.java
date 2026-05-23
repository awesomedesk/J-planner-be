package com.awesomedesk.j_planner.v1.calendar.domain;

import com.awesomedesk.j_planner.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

@Getter
@Builder
@ToString(exclude = {"calendar"})
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "calendar_details")
public class CalendarDetail extends BaseEntity {

    @Id
    @Column(name = "calendar_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "calendar_id")
    private Calendar calendar;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "text_location", columnDefinition = "TEXT")
    private String textLocation;

    @Column(name = "location", columnDefinition = "POINT")
    private Point location;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public void update(String description, String textLocation, Double latitude, Double longitude) {
        this.description = description;
        this.textLocation = textLocation;
        this.location = toPoint(latitude, longitude);
    }

    public void delete() {
        this.setDeleted(true);
        this.setDeletedAt(LocalDateTime.now());
    }

    public Double getLatitude() {
        return location == null ? null : location.getY();
    }

    public Double getLongitude() {
        return location == null ? null : location.getX();
    }

    private static Point toPoint(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        // MySQL POINT: x=longitude, y=latitude
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }
}
