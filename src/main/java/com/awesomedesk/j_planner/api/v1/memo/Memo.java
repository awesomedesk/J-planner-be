package com.awesomedesk.j_planner.api.v1.memo;

import com.awesomedesk.j_planner.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** 메모 (07-db-design.md 4-7). 날짜와 연결되지 않는다 (D-011) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "memos")
@SQLDelete(sql = "UPDATE memos SET deleted = 'Y', deleted_at = NOW() WHERE memo_id = ?")
@SQLRestriction("deleted = 'N'")
public class Memo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long id;

    /** 회원번호 (US-32). 만든 뒤 바뀌지 않는다 */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    Memo(Long userId, MemoRules.Normalized v) {
        this.userId = userId;
        apply(v);
    }

    void apply(MemoRules.Normalized v) {
        this.title = v.title();
        this.content = v.content();
    }
}
