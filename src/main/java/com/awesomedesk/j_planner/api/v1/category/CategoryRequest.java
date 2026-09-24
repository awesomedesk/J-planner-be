package com.awesomedesk.j_planner.api.v1.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 카테고리 추가 요청. PATCH는 현재 값에 보낸 필드를 합친 뒤 이 모양으로 검증한다.
 * 이름 앞뒤 공백은 지운다 (08-api-design.md 3절).
 */
public record CategoryRequest(
    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 50, message = "이름은 50자까지입니다.")
    String name,

    @NotNull(message = "색을 고르세요.")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색은 #RRGGBB 형식입니다.")
    String color
) {

    public CategoryRequest {
        name = name == null ? null : name.strip();
    }
}
