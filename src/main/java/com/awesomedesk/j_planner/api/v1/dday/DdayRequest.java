package com.awesomedesk.j_planner.api.v1.dday;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * D-Day 추가 요청 / PATCH 합친 결과 (08-api-design.md 6절).
 * 추가할 때 countType을 안 보내면 COUNTDOWN, display를 안 보내면(또는 일부만 보내면) 기준별 기본값으로 채운다 (D-020).
 */
public record DdayRequest(
    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 255, message = "제목은 255자까지입니다.")
    String title,

    @NotNull(message = "목표 날짜를 입력하세요.")
    LocalDate targetDate,

    CountType countType,

    @Valid
    Display display
) {

    public DdayRequest {
        title = title == null ? null : title.strip();
    }

    /** 달력 표시 옵션 (D-020). 목표 날짜 칸은 옵션과 관계없이 항상 표시 */
    public record Display(
        /* N일 단위 */
        @Valid Option interval,
        /* 마지막 N일은 매일 (COUNTDOWN만) */
        @Valid Option lastDays,
        /* 매일, 등록일부터 (COUNTDOWN만) */
        Boolean daily,
        /* 매년 n주년 (COUNTUP만) */
        Boolean yearly
    ) {
    }

    public record Option(
        Boolean enabled,
        @Min(value = 1, message = "일 수는 1 이상입니다.")
        Integer days
    ) {
    }
}
