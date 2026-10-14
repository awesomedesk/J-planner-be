package com.awesomedesk.j_planner.api.v1.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 로그인 요청 (08 13-3). 비밀번호 규칙(15~128자)은 가입·변경 때 검사하고 로그인은 길이 상한만 */
public record LoginRequest(
    @NotBlank(message = "이메일을 입력하세요.")
    @Size(max = 254, message = "이메일은 254자까지입니다.")
    String email,

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(max = 1024, message = "비밀번호가 너무 깁니다.")
    String password
) {
}
