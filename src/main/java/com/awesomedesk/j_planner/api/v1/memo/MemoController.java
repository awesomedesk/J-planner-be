package com.awesomedesk.j_planner.api.v1.memo;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 메모 API (US-25). 명세: j-planner-product/08-api-design.md 8절 */
@RestController
@RequestMapping("/api/v1/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    @GetMapping
    public List<MemoResponse> list() {
        return memoService.list();
    }

    @GetMapping("/{id}")
    public MemoResponse get(@PathVariable Long id) {
        return memoService.get(id);
    }

    @PostMapping
    public ResponseEntity<MemoResponse> create(@Valid @RequestBody MemoRequest request) {
        MemoResponse created = memoService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/memos/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public MemoResponse update(@PathVariable Long id, @RequestBody JsonNode patch) {
        return memoService.update(id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
