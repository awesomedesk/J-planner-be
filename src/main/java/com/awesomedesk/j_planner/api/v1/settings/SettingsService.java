package com.awesomedesk.j_planner.api.v1.settings;

import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import java.time.DayOfWeek;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 설정 (US-20·21·26·27, 08-api-design.md 9절)
 * - 바뀐 항목만 PATCH로 받아 바로 저장 (SET-05)
 * - sidebarItems는 4개 전체를 순서대로 받아 통째로 바꾼다
 * - '마지막에 본 화면'의 값은 서버에 없다 (FE localStorage, D-029)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {

    private final UserSettingsRepository settingsRepository;
    private final SidebarItemStore sidebarItemStore;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;

    public SettingsResponse get() {
        return SettingsResponse.of(find(), sidebarItemStore.find(UserSettings.ID));
    }

    @Transactional
    public SettingsResponse update(JsonNode patch) {
        UserSettings settings = find();
        SettingsRequest merged = requestValidator.validate(
            jsonMergePatch.apply(get().toRequest(), patch, SettingsRequest.class));
        SettingsRules.check(merged);

        settings.apply(merged);
        settingsRepository.flush();
        if (patch.has("sidebarItems")) {
            sidebarItemStore.replace(UserSettings.ID, merged.sidebarItems());
        }
        return get();
    }

    /** 주 시작 요일 (CAL-04). 주간 Todo 날짜 검사에 쓴다 (D-041) */
    public DayOfWeek weekStartDay() {
        return find().getWeekStartDay().toDayOfWeek();
    }

    private UserSettings find() {
        return settingsRepository.findById(UserSettings.ID)
            .orElseThrow(() -> new IllegalStateException("user_settings 기본 행(setting_id = 1)이 없습니다."));
    }
}
