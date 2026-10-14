package com.awesomedesk.j_planner.api.v1.auth;

/** 같은 계정으로 연속 실패하면 점점 늦춘다 (08 13-7): 5회부터 30초, 10회부터 5분, 20회부터 30분. 영구 잠금 없음 */
final class LoginBackoff {

    private LoginBackoff() {
    }

    static long waitSeconds(int failures) {
        if (failures >= 20) {
            return 30 * 60;
        }
        if (failures >= 10) {
            return 5 * 60;
        }
        if (failures >= 5) {
            return 30;
        }
        return 0;
    }
}
