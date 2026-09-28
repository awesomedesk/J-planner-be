package com.awesomedesk.j_planner.api.v1.settings;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자 설정 (07-db-design.md 4-9). MVP는 한 줄(setting_id = 1)뿐이고 삭제하지 않는다.
 * 사이드바 항목은 {@link SidebarItemStore}가 따로 읽고 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_settings")
@EntityListeners(AuditingEntityListener.class)
public class UserSettings {

    public static final long ID = 1L;

    @Id
    @Column(name = "setting_id")
    private Long id;

    @Column(name = "week_start_day", nullable = false, length = 3)
    private String weekStartDay;

    @Column(name = "start_view", nullable = false, length = 10)
    private String startView;

    @Column(name = "time_format", nullable = false, length = 3)
    private String timeFormat;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "dark_mode", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean darkMode;

    @Column(name = "color_theme", nullable = false, length = 10)
    private String colorTheme;

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
