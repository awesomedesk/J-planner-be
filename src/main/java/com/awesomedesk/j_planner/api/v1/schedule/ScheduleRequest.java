package com.awesomedesk.j_planner.api.v1.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 일정 추가 요청. PATCH는 현재 값에 보낸 필드를 합친 뒤 이 모양으로 검증한다 (08-api-design.md 4절).
 */
public record ScheduleRequest(
    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 255, message = "제목은 255자까지입니다.")
    String title,

    @NotNull(message = "종일 여부를 보내세요.")
    Boolean allDay,

    @NotNull(message = "시작 일시를 입력하세요.")
    LocalDateTime start,

    @NotNull(message = "종료 일시를 입력하세요.")
    LocalDateTime end,

    /** 없으면 미지정 */
    Long categoryId,

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색은 #RRGGBB 형식입니다.")
    String color,

    String description,

    @Valid
    Location location,

    @Size(max = 2048, message = "URL은 2048자까지입니다.")
    @Pattern(regexp = "^https?://\\S+$", message = "URL은 http:// 또는 https://로 시작해야 합니다.")
    String url
) {

    public record Location(
        String name,

        @DecimalMin(value = "-90", message = "위도는 -90~90입니다.")
        @DecimalMax(value = "90", message = "위도는 -90~90입니다.")
        Double latitude,

        @DecimalMin(value = "-180", message = "경도는 -180~180입니다.")
        @DecimalMax(value = "180", message = "경도는 -180~180입니다.")
        Double longitude
    ) {
    }
}
