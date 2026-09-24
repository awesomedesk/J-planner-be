package com.awesomedesk.j_planner.api.v1.schedule;

import java.time.LocalDateTime;

public record ScheduleResponse(
    Long id,
    String title,
    boolean allDay,
    LocalDateTime start,
    LocalDateTime end,
    Long categoryId,
    String color,
    String description,
    Location location,
    String url
) {

    public record Location(String name, Double latitude, Double longitude) {
    }

    static ScheduleResponse of(Schedule s) {
        Location location = null;
        if (s.getLocationName() != null || s.getLocationPoint() != null) {
            Double lat = s.getLocationPoint() == null ? null : s.getLocationPoint().getY();
            Double lng = s.getLocationPoint() == null ? null : s.getLocationPoint().getX();
            location = new Location(s.getLocationName(), lat, lng);
        }
        return new ScheduleResponse(s.getId(), s.getTitle(), s.isAllDay(), s.getStartDateTime(), s.getEndDateTime(),
            s.getCategoryId(), s.getColor(), s.getDescription(), location, s.getUrl());
    }

    /** PATCH 합치기용: 현재 값을 요청 모양으로 */
    ScheduleRequest toRequest() {
        ScheduleRequest.Location loc = location == null ? null
            : new ScheduleRequest.Location(location.name(), location.latitude(), location.longitude());
        return new ScheduleRequest(title, allDay, start, end, categoryId, color, description, loc, url);
    }
}
