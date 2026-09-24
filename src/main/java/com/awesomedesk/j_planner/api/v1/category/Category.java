package com.awesomedesk.j_planner.api.v1.category;

import com.awesomedesk.j_planner.common.converter.attribute.BooleanToStringConverter;
import com.awesomedesk.j_planner.common.domain.BaseEntity;
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

/**
 * 카테고리 (07-db-design.md 4-1). 생성 컬럼 default_guard·active_name은 DB가 계산하므로 매핑하지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "categories")
@SQLDelete(sql = "UPDATE categories SET deleted = 'Y', deleted_at = NOW() WHERE category_id = ?")
@SQLRestriction("deleted = 'N'")
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** null = 고르지 않음 (화면 기본색 #2F62A8, D-037) */
    @Column(length = 7)
    private String color;

    @Column(name = "is_default", nullable = false, length = 1)
    @Convert(converter = BooleanToStringConverter.class)
    private boolean isDefault;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Category(String name, String color, int sortOrder) {
        this.name = name;
        this.color = color;
        this.isDefault = false;
        this.sortOrder = sortOrder;
    }

    public void change(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
