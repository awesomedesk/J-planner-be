package com.awesomedesk.j_planner.api.v1.settings;

import com.awesomedesk.j_planner.common.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 설정 API (US-20·21·26·27). 명세: j-planner-product/08-api-design.md 9절 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public SettingsResponse get(AuthUser user) {
        return settingsService.get(user.userId());
    }

    @PatchMapping
    public SettingsResponse update(AuthUser user, @RequestBody JsonNode patch) {
        return settingsService.update(user.userId(), patch);
    }
}
