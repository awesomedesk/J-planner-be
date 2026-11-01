package com.awesomedesk.j_planner.api.v1.memo;

import com.awesomedesk.j_planner.common.domain.BaseEntity;
import com.awesomedesk.j_planner.common.crypto.EncryptedTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    /** 제목·내용은 앱에서 암호화해 암호문 칸에 저장 (US-41) */
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "title_enc", columnDefinition = "VARBINARY(1100)")
    private String title;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "content_enc", columnDefinition = "MEDIUMBLOB")
    private String content;

    /** 암호화 전 원문 칸 — 켤 때 NotesEncryptor가 옮기고, 저장할 때도 비운다 (07 11-8) */
    @Getter(AccessLevel.NONE)
    @Column(name = "title")
    private String plainTitle;

    @Getter(AccessLevel.NONE)
    @Column(name = "content", columnDefinition = "TEXT")
    private String plainContent;

    Memo(Long userId, MemoRules.Normalized v) {
        this.userId = userId;
        apply(v);
    }

    /** 암호문이 있으면 그것, 아직 옮기지 않은 원문이면 원문 */
    public String getTitle() {
        return title != null ? title : plainTitle;
    }

    public String getContent() {
        return content != null ? content : plainContent;
    }

    void apply(MemoRules.Normalized v) {
        this.title = v.title();
        this.content = v.content();
        this.plainTitle = null;
        this.plainContent = null;
    }
}
