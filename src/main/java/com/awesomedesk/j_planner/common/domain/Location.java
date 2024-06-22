package com.awesomedesk.j_planner.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class Location {

    @Column(name = "LOCATION_NAME")
    private String name;
    @Column(precision = 10, scale = 6)
    private float latitude;
    @Column(precision = 10, scale = 6)
    private float longitude;

    public Location(String name, float latitude, float longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
