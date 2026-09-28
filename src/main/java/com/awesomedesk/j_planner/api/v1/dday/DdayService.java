package com.awesomedesk.j_planner.api.v1.dday;

import com.awesomedesk.j_planner.common.api.DateRanges;
import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.Positions;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * D-Day (US-22, 08-api-design.md 6절)
 * - 목록은 사용자 순서, 남은 날·지난 날 구분 없이 한 목록, 새 D-Day는 맨 뒤 (D-029)
 * - 추가할 때 표시 옵션을 안 보내면 기준별 기본값 (D-020)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DdayService {

    private final DdayRepository ddayRepository;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;
    private final Clock clock;

    public List<DdayResponse> list() {
        LocalDate today = today();
        return ddayRepository.findAllOrdered().stream().map(d -> DdayResponse.of(d, today)).toList();
    }

    public DdayResponse get(Long id) {
        return DdayResponse.of(find(id), today());
    }

    @Transactional
    public DdayResponse create(DdayRequest r) {
        CountType type = r.countType() == null ? CountType.COUNTDOWN : r.countType();
        DdayRequest.Display display = DdayDisplayRules.fill(type, r.display());
        DdayDisplayRules.check(type, display);
        int sortOrder = ddayRepository.findMaxSortOrder() + 1;
        Dday saved = ddayRepository.save(new Dday(new Dday.Values(r.title(), r.targetDate(), type, display), sortOrder));
        return DdayResponse.of(saved, today());
    }

    @Transactional
    public DdayResponse update(Long id, JsonNode patch) {
        Dday dday = find(id);
        LocalDate today = today();
        DdayRequest merged = requestValidator.validate(
            jsonMergePatch.apply(DdayResponse.of(dday, today).toRequest(), patch, DdayRequest.class));
        if (merged.countType() == null) {
            throw ApiException.validation("countType", "계산 기준을 고르세요.");
        }
        if (merged.display() == null) {
            throw ApiException.validation("display", "달력 표시 옵션이 필요합니다.");
        }
        DdayRequest.Display display = DdayDisplayRules.fill(merged.countType(), merged.display());
        DdayDisplayRules.check(merged.countType(), display);
        dday.apply(new Dday.Values(merged.title(), merged.targetDate(), merged.countType(), display));
        ddayRepository.flush();
        return DdayResponse.of(dday, today);
    }

    @Transactional
    public void delete(Long id) {
        ddayRepository.delete(find(id));
    }

    /** 순서 이동 (D-030). afterId 바로 뒤로 옮기고 0부터 다시 매긴다 */
    @Transactional
    public DdayResponse move(Long id, Long afterId) {
        Dday dday = find(id);
        Positions.reorder(ddayRepository.findAllOrdered(), dday, afterId, 0);
        ddayRepository.flush();
        return DdayResponse.of(dday, today());
    }

    /** 달력 표시 (US-23). 날짜 순, 같은 날은 D-Day 순서 */
    public List<DdayMarkResponse> marks(LocalDate from, LocalDate to) {
        DateRanges.check(from, to);
        List<DdayMarkResponse> result = new ArrayList<>();
        for (Dday d : ddayRepository.findAllOrdered()) {
            DdayMarkCalculator.Spec spec = new DdayMarkCalculator.Spec(d.getCountType(), d.getTargetDate(), d.display(),
                d.getCreatedAt().toLocalDate());
            for (DdayMarkCalculator.Mark m : DdayMarkCalculator.marks(spec, from, to)) {
                result.add(new DdayMarkResponse(d.getId(), m.date(), m.label(), m.kind()));
            }
        }
        // findAllOrdered가 D-Day 순서라, 날짜로 안정 정렬하면 같은 날은 D-Day 순서가 유지된다
        result.sort(Comparator.comparing(DdayMarkResponse::date));
        return result;
    }

    private Dday find(Long id) {
        return ddayRepository.findById(id).orElseThrow(() -> ApiException.notFound("D-Day를 찾을 수 없습니다: " + id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
