package com.awesomedesk.j_planner.common.domain;

/** 사용자 순서(sort_order)를 가진 항목: 카테고리·Todo·D-Day (D-029, D-030) */
public interface Sortable {

    Long getId();

    void changeSortOrder(int sortOrder);
}
