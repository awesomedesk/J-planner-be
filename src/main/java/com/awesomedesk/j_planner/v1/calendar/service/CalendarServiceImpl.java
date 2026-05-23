package com.awesomedesk.j_planner.v1.calendar.service;

import com.awesomedesk.j_planner.common.domain.DateDto;
import com.awesomedesk.j_planner.v1.calendar.domain.Calendar;
import com.awesomedesk.j_planner.v1.calendar.domain.CalendarDetail;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarDetailDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarInfoDto;
import com.awesomedesk.j_planner.v1.calendar.repository.CalendarRepository;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarCreateReqDto;
import com.awesomedesk.j_planner.v1.calendar.dto.CalendarUpdateReqDto;
import com.awesomedesk.j_planner.v1.calendar.mapper.CalendarMapper;
import com.awesomedesk.j_planner.common.exception.custom.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final CalendarRepository calendarRepository;
    private final CalendarMapper calendarMapper;

    @Override
    public List<CalendarInfoDto> getCalendarList(DateDto input) {
        DateDto dateDto = normalizeDateDto(input);
        log.info("CalendarServiceImpl getCalendarList - dateDto : {}", dateDto);
        List<Calendar> scheduleList = calendarRepository.findAllByDateDto(dateDto);
        return calendarMapper.toInfoDtoList(scheduleList);
    }

    private DateDto normalizeDateDto(DateDto input) {
        if (input == null) {
            input = new DateDto();
        }

        LocalDateTime startDateTime = input.getStartDateTime();
        LocalDateTime endDateTime = input.getEndDateTime();

        if (startDateTime == null && endDateTime == null) {
            LocalDate today = LocalDate.now();
            LocalDate firstDay = today.withDayOfMonth(1);
            LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfMonth());
            return new DateDto(
                firstDay.atStartOfDay(),
                LocalDateTime.of(lastDay, LocalTime.of(23, 59, 59))
            );
        }

        if (startDateTime == null) {
            LocalDate endDate = endDateTime.toLocalDate();
            LocalDate firstDay = endDate.withDayOfMonth(1);
            startDateTime = firstDay.atStartOfDay();
        }

        if (endDateTime == null) {
            LocalDate startDate = startDateTime.toLocalDate();
            LocalDate lastDay = startDate.with(TemporalAdjusters.lastDayOfMonth());
            endDateTime = LocalDateTime.of(lastDay, LocalTime.of(23, 59, 59));
        }


        return new DateDto(startDateTime, endDateTime);
    }

    @Override
    @Transactional()
    public Long postCalendar(CalendarCreateReqDto input) {
        // Create Calendar entity
        Calendar calendar = Calendar.builder()
            .title(input.getTitle())
            .color(input.getColor())
            .allDay(input.isAllDay())
            .dateDto(new DateDto(input.getStartDatetime(), input.getEndDatetime()))
            .build();

            // Create CalendarDetail entity if detail info exists
        if (input.getDetail() != null) {
            CalendarDetailDto detailDto = input.getDetail();
            CalendarDetail detail = CalendarDetail.builder()
                .description(detailDto.getDescription())
                .textLocation(detailDto.getTextLocation())
                .build();
            detail.update(
                detailDto.getDescription(),
                detailDto.getTextLocation(),
                detailDto.getLatitude(),
                detailDto.getLongitude()
            );

            calendar.setDetail(detail);
        }

        Calendar saved = calendarRepository.save(calendar);
        return saved.getId();
    }

    @Override
    @Transactional()
    public Long updateCalendar(Long calenderId, CalendarUpdateReqDto input) {
        Calendar calendar = calendarRepository.findById(calenderId)
            .orElseThrow(() -> new EntityNotFoundException("Calendar not found with id: " + calenderId));

        // Update basic calendar info
        calendar.update(
            input.getTitle(),
            input.getColor(),
            input.isAllDay(),
            input.getStartDatetime(),
            input.getEndDatetime()
        );

        // Update detail info if exists
        if (input.getDetail() != null) {
            CalendarDetailDto detailDto = input.getDetail();

            if (calendar.getDetail() != null) {
                // Update existing detail
                calendar.getDetail().update(
                    detailDto.getDescription(),
                    detailDto.getTextLocation(),
                    detailDto.getLatitude(),
                    detailDto.getLongitude()
                );
            } else {
                // Create new detail
                CalendarDetail detail = CalendarDetail.builder()
                    .build();
                detail.update(
                    detailDto.getDescription(),
                    detailDto.getTextLocation(),
                    detailDto.getLatitude(),
                    detailDto.getLongitude()
                );

                calendar.setDetail(detail);
            }
        }

        Calendar updated = calendarRepository.save(calendar);
        return updated.getId();
    }

    @Override
    @Transactional()
    public void deleteCalendar(Long calenderId) {
        Calendar calendar = calendarRepository.findById(calenderId)
            .orElseThrow(() -> new EntityNotFoundException("Calendar not found with id: " + calenderId));

        calendar.delete();
        calendarRepository.save(calendar);
    }
}
