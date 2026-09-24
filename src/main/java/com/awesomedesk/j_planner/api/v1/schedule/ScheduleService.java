package com.awesomedesk.j_planner.api.v1.schedule;

import com.awesomedesk.j_planner.api.v1.category.CategoryRepository;
import com.awesomedesk.j_planner.common.api.DateRanges;
import com.awesomedesk.j_planner.common.api.JsonMergePatch;
import com.awesomedesk.j_planner.common.api.RequestValidator;
import com.awesomedesk.j_planner.common.error.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 일정 (US-05, 08-api-design.md 4절)
 * - 종일이면 시작 = 그날 00:00:00, 종료 = 끝나는 날 23:59:59로 맞춘다
 * - 카테고리를 안 보내면(또는 null) 미지정 (D-014)
 * - 색 null = 테마 Theme2 (D-030)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private static final GeometryFactory GEOMETRY = new GeometryFactory();
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final ScheduleRepository scheduleRepository;
    private final CategoryRepository categoryRepository;
    private final JsonMergePatch jsonMergePatch;
    private final RequestValidator requestValidator;

    public List<ScheduleResponse> list(LocalDate from, LocalDate to, List<Long> categoryIds) {
        DateRanges.check(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<Schedule> schedules = (categoryIds == null || categoryIds.isEmpty())
            ? scheduleRepository.findOverlapping(start, end)
            : scheduleRepository.findOverlapping(start, end, categoryIds);
        return schedules.stream().map(ScheduleResponse::of).toList();
    }

    public ScheduleResponse get(Long id) {
        return ScheduleResponse.of(find(id));
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        Schedule saved = scheduleRepository.save(new Schedule(toValues(request)));
        return ScheduleResponse.of(saved);
    }

    @Transactional
    public ScheduleResponse update(Long id, JsonNode patch) {
        Schedule schedule = find(id);
        ScheduleRequest merged = requestValidator.validate(
            jsonMergePatch.apply(ScheduleResponse.of(schedule).toRequest(), patch, ScheduleRequest.class));
        schedule.apply(toValues(merged));
        scheduleRepository.flush();
        return ScheduleResponse.of(schedule);
    }

    @Transactional
    public void delete(Long id) {
        Schedule schedule = find(id);
        scheduleRepository.delete(schedule);
    }

    private Schedule find(Long id) {
        return scheduleRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("일정을 찾을 수 없습니다: " + id));
    }

    /** 검증(필드 사이 규칙 포함)과 정리를 마친 값 */
    private Schedule.Values toValues(ScheduleRequest r) {
        boolean allDay = r.allDay();
        LocalDateTime start = r.start().withNano(0);
        LocalDateTime end = r.end().withNano(0);
        if (allDay) {
            start = start.toLocalDate().atStartOfDay();
            end = end.toLocalDate().atTime(END_OF_DAY);
            if (end.toLocalDate().isBefore(start.toLocalDate())) {
                throw ApiException.validation("end", "종료 날짜는 시작 날짜와 같거나 뒤여야 합니다.");
            }
        } else if (!end.isAfter(start)) {
            throw ApiException.validation("end", "종료 일시는 시작 일시보다 뒤여야 합니다.");
        }

        Long categoryId = r.categoryId();
        if (categoryId == null) {
            categoryId = categoryRepository.getDefault().getId();
        } else if (!categoryRepository.existsById(categoryId)) {
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

        return new Schedule.Values(categoryId, r.title().strip(), allDay, start, end, r.color(), r.description(),
            locationName, point, r.url());
    }
}
