package com.awesomedesk.j_planner.api.v1.diary;

import com.awesomedesk.j_planner.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** 일기 (07-db-design.md 4-6). 삭제 안 된 일기는 날짜당 1개 (생성 컬럼 active_date UNIQUE, 매핑 안 함) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "diaries")
@SQLDelete(sql = "UPDATE diaries SET deleted = 'Y', deleted_at = NOW() WHERE diary_id = ?")
@SQLRestriction("deleted = 'N'")
public class Diary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long id;

    @Column(name = "diary_date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public Diary(LocalDate date, String content) {
        this.date = date;
        this.content = content;
    }

    public void changeContent(String content) {
        this.content = content;
    }
}
