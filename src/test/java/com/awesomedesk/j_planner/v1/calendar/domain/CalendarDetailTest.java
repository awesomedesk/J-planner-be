package com.awesomedesk.j_planner.v1.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CalendarDetail 좌표 변환 테스트")
class CalendarDetailTest {

    @Test
    @DisplayName("latitude/longitude 입력이 MySQL POINT(x=lon,y=lat)로 저장된다")
    void update_setsPointAsLonLat() {
        CalendarDetail detail = new CalendarDetail();

        detail.update("desc", "text", 37.5665, 126.9780);

        assertThat(detail.getLongitude()).isEqualTo(126.9780);
        assertThat(detail.getLatitude()).isEqualTo(37.5665);
    }
}

