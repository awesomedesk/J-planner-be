package com.awesomedesk.j_planner.api.v1.category;

public record CategoryResponse(
    Long id,
    String name,
    String color,
    boolean isDefault,
    int sortOrder,
    long scheduleCount,
    long todoCount
) {

    static CategoryResponse of(Category c, long scheduleCount, long todoCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getColor(), c.isDefault(), c.getSortOrder(),
            scheduleCount, todoCount);
    }
}
