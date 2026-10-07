package com.awesomedesk.j_planner.api.v1.settings;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자 설정 (07-db-design.md 4-9, 11-3). 회원마다 한 줄(기본 키 = user_id). 가입 때 만들어진다 (UserDataInitializer).
 * 사이드바 항목은 {@link SidebarItemStore}가 따로 읽고 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_settings")
@EntityListeners(AuditingEntityListener.class)
public class UserSettings {

    /** 회원번호 */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_start_day", nullable = false, length = 3)
    private WeekStartDay weekStartDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_view", nullable = false, length = 10)
    private StartView startView;

    @Convert(converter = TimeFormat.DbConverter.class)
    @Column(name = "time_format", nullable = false, length = 3)
    private TimeFormat timeFormat;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "dark_mode", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean darkMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_theme", nullable = false, length = 10)
    private ColorTheme colorTheme;

    @Column(name = "sidebar_open", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean sidebarOpen;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 검증을 마친 값으로 바꾼다 */
    void apply(SettingsRequest r) {
        this.weekStartDay = r.weekStartDay();
        this.startView = r.startView();
        this.timeFormat = r.timeFormat();
        this.slotMinutes = r.slotMinutes();
        this.darkMode = r.darkMode();
        this.colorTheme = r.colorTheme();
        this.sidebarOpen = r.sidebarOpen();
    }
}
