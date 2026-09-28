package com.awesomedesk.j_planner.api.v1.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 시간 표시 (SET-03). JSON·DB 글자는 "24H"·"12H" (이름이 숫자로 시작할 수 없어 따로 둔다) */
public enum TimeFormat {
    @JsonProperty("24H") H24("24H"),
    @JsonProperty("12H") H12("12H");

    private final String code;

    TimeFormat(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    static TimeFormat fromCode(String code) {
        for (TimeFormat f : values()) {
            if (f.code.equals(code)) {
                return f;
            }
        }
        throw new IllegalArgumentException("알 수 없는 시간 표시: " + code);
    }

    /** DB time_format 컬럼 ↔ enum */
    @Converter
    public static class DbConverter implements AttributeConverter<TimeFormat, String> {
        @Override
        public String convertToDatabaseColumn(TimeFormat attribute) {
            return attribute == null ? null : attribute.code;
        }

        @Override
        public TimeFormat convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromCode(dbData);
        }
    }
}
