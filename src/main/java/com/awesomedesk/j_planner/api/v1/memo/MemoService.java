package com.awesomedesk.j_planner.api.v1.memo;

import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 메모 (US-25, 08-api-design.md 8절)
 * - 목록은 최근 수정 순 (D-029), 저장 버튼으로만 저장·검색 없음 (D-030)
 * - 빈 메모는 저장하지 않는다 (D-032)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoService {

    private final MemoRepository memoRepository;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;

    public List<MemoResponse> list() {
        return memoRepository.findAllByOrderByUpdatedAtDescIdDesc().stream().map(MemoResponse::of).toList();
    }

    public MemoResponse get(Long id) {
        return MemoResponse.of(find(id));
    }

    @Transactional
    public MemoResponse create(MemoRequest r) {
        Memo saved = memoRepository.saveAndFlush(new Memo(MemoRules.normalize(r.title(), r.content())));
        return MemoResponse.of(saved);
    }

    @Transactional
    public MemoResponse update(Long id, JsonNode patch) {
        Memo memo = find(id);
        MemoRequest merged = requestValidator.validate(
            jsonMergePatch.apply(MemoResponse.of(memo).toRequest(), patch, MemoRequest.class));
        memo.apply(MemoRules.normalize(merged.title(), merged.content()));
        memoRepository.flush();
        return MemoResponse.of(memo);
    }

    @Transactional
    public void delete(Long id) {
        memoRepository.delete(find(id));
    }

    private Memo find(Long id) {
        return memoRepository.findById(id).orElseThrow(() -> ApiException.notFound("메모를 찾을 수 없습니다: " + id));
    }
}
