package com.awesomedesk.j_planner.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** JPA 칸 하나를 {@link DataCipher}로 암호화해 저장한다 (일기 내용, 메모 제목·내용 — US-41). Hibernate가 Spring 빈으로 만든다 */
@Converter
public class EncryptedTextConverter implements AttributeConverter<String, byte[]> {

    private final DataCipher cipher;

    public EncryptedTextConverter(DataCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}
