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
 * 메모 (US-25, 08-api-design.md 8절). 모두 로그인한 회원(userId)의 메모만 다룬다 (US-32)
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

    public List<MemoResponse> list(Long userId) {
        return memoRepository.findAllByUserIdOrderByUpdatedAtDescIdDesc(userId).stream().map(MemoResponse::of).toList();
    }

    public MemoResponse get(Long userId, Long id) {
        return MemoResponse.of(find(userId, id));
    }

    @Transactional
    public MemoResponse create(Long userId, MemoRequest r) {
        Memo saved = memoRepository.saveAndFlush(new Memo(userId, MemoRules.normalize(r.title(), r.content())));
        return MemoResponse.of(saved);
    }

    @Transactional
    public MemoResponse update(Long userId, Long id, JsonNode patch) {
        Memo memo = find(userId, id);
        MemoRequest merged = requestValidator.validate(
            jsonMergePatch.apply(MemoResponse.of(memo).toRequest(), patch, MemoRequest.class));
        memo.apply(MemoRules.normalize(merged.title(), merged.content()));
        memoRepository.flush();
        return MemoResponse.of(memo);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        memoRepository.delete(find(userId, id));
    }

    /** 남의 메모도 '없음'(404) */
    private Memo find(Long userId, Long id) {
        return memoRepository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("메모를 찾을 수 없습니다: " + id));
    }
}
