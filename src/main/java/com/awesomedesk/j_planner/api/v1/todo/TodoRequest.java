package com.awesomedesk.j_planner.api.v1.todo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Todo 추가 요청. PATCH는 현재 값에 보낸 필드를 합친 뒤 이 모양으로 검증한다 (08-api-design.md 5절).
 * {@code completed}는 PATCH에서만 쓴다 (추가할 때는 항상 미완료).
 */
public record TodoRequest(
    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 255, message = "제목은 255자까지입니다.")
    String title,

    @NotNull(message = "종류를 고르세요.")
    TodoType type,

    @NotNull(message = "시작일을 입력하세요.")
    LocalDate startDate,

    @NotNull(message = "마감일을 입력하세요.")
    LocalDate endDate,

    /** null = 시간 지정 안 함 */
    @Valid
    Time time,

    /** 없으면 미지정 */
    Long categoryId,

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색은 #RRGGBB 형식입니다.")
    String color,

    Boolean completed
) {

    public record Time(
        @NotNull(message = "시작 시각을 입력하세요.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime start,

        @NotNull(message = "시간 길이를 입력하세요.")
        @Min(value = 1, message = "시간 길이는 1~1440분입니다.")
        @Max(value = 1440, message = "시간 길이는 1~1440분입니다.")
        Integer durationMinutes
    ) {
    }
}
