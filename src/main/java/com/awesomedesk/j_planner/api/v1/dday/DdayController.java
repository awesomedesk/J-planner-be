package com.awesomedesk.j_planner.api.v1.dday;

import com.awesomedesk.j_planner.common.api.PositionRequest;
import com.awesomedesk.j_planner.common.auth.AuthUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** D-Day API (US-22). 명세: j-planner-product/08-api-design.md 6절 */
@RestController
@RequestMapping("/api/v1/ddays")
@RequiredArgsConstructor
public class DdayController {

    private final DdayService ddayService;

    @GetMapping
    public List<DdayResponse> list(AuthUser user) {
        return ddayService.list(user.userId());
    }

    @GetMapping("/{id}")
    public DdayResponse get(AuthUser user, @PathVariable Long id) {
        return ddayService.get(user.userId(), id);
    }

    @PostMapping
    public ResponseEntity<DdayResponse> create(AuthUser user, @Valid @RequestBody DdayRequest request) {
        DdayResponse created = ddayService.create(user.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/ddays/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public DdayResponse update(AuthUser user, @PathVariable Long id, @RequestBody JsonNode patch) {
        return ddayService.update(user.userId(), id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(AuthUser user, @PathVariable Long id) {
        ddayService.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/position")
    public DdayResponse move(AuthUser user, @PathVariable Long id, @RequestBody PositionRequest request) {
        return ddayService.move(user.userId(), id, request.afterId());
    }
}
