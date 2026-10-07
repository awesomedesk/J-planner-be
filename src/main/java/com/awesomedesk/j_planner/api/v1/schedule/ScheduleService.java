package com.awesomedesk.j_planner.api.v1.schedule;

import com.awesomedesk.j_planner.api.v1.category.CategoryRepository;
import com.awesomedesk.j_planner.common.api.DateRanges;
import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 일정 (US-05, 08-api-design.md 4절). 모두 로그인한 회원(userId)의 일정만 다룬다 (US-32)
 * - 종일이면 시작 = 그날 00:00:00, 종료 = 끝나는 날 23:59:59로 맞춘다
 * - 카테고리를 안 보내면(또는 null) 미지정 (D-014)
 * - 색 null = 테마 Theme2 (D-030)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private static final GeometryFactory GEOMETRY = new GeometryFactory();

    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;

    public List<ScheduleResponse> list(Long userId, LocalDate from, LocalDate to, List<Long> categoryIds) {
        DateRanges.check(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<Schedule> schedules = (categoryIds == null || categoryIds.isEmpty())
            ? scheduleRepository.findOverlapping(userId, start, end)
            : scheduleRepository.findOverlapping(userId, start, end, categoryIds);
        return schedules.stream().map(ScheduleResponse::of).toList();
    }

    public ScheduleResponse get(Long userId, Long id) {
        return ScheduleResponse.of(find(userId, id));
    }

    @Transactional
    public ScheduleResponse create(Long userId, ScheduleRequest request) {
        Schedule saved = scheduleRepository.save(new Schedule(userId, toValues(userId, request)));
        return ScheduleResponse.of(saved);
    }

    @Transactional
    public ScheduleResponse update(Long userId, Long id, JsonNode patch) {
        Schedule schedule = find(userId, id);
        ScheduleRequest merged = requestValidator.validate(
            jsonMergePatch.apply(ScheduleResponse.of(schedule).toRequest(), patch, ScheduleRequest.class));
        schedule.apply(toValues(userId, merged));
        scheduleRepository.flush();
        return ScheduleResponse.of(schedule);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Schedule schedule = find(userId, id);
        scheduleRepository.delete(schedule);
    }

    /** 남의 일정도 '없음'(404) */
    private Schedule find(Long userId, Long id) {
        return scheduleRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> ApiException.notFound("일정을 찾을 수 없습니다: " + id));
    }

    /** 검증(필드 사이 규칙 포함)과 정리를 마친 값 */
    private Schedule.Values toValues(Long userId, ScheduleRequest r) {
        boolean allDay = r.allDay();
        ScheduleTimes times = ScheduleTimes.of(allDay, r.start(), r.end());

        Long categoryId = r.categoryId();
        if (categoryId == null) {
            categoryId = categoryRepository.getDefault(userId).getId();
        } else if (!categoryRepository.existsByIdAndUserId(categoryId, userId)) { // 남의 카테고리도 '없는 카테고리'
            throw ApiException.validation("categoryId", "없는 카테고리입니다.");
        }

        String locationName = null;
        Point point = null;
        if (r.location() != null) {
            ScheduleRequest.Location loc = r.location();
            if ((loc.latitude() == null) != (loc.longitude() == null)) {
                throw ApiException.validation("location", "위도와 경도는 함께 입력하세요.");
            }
            locationName = loc.name();
            if (loc.latitude() != null) {
                point = GEOMETRY.createPoint(new Coordinate(loc.longitude(), loc.latitude()));
            }
        }

        return new Schedule.Values(categoryId, r.title().strip(), allDay, times.start(), times.end(),
            r.color(), r.description(),
            locationName, point, r.url());
    }
}
