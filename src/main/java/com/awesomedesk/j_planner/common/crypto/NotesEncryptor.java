package com.awesomedesk.j_planner.common.crypto;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 암호화 전에 저장된 일기·메모 원문을 서버를 켤 때 한 번 읽어 암호화해 다시 저장한다 (US-41 AC4, D-066: 기존 admin 데이터, 로컬 예시 데이터).
 * SQL만으로는 암호화할 수 없어서 Flyway(V8: 칸 타입, V10: 원문 표시 0) 대신 앱이 한다. <b>표시(첫 바이트 0)가 있는 행만</b> 바꾼다 —
 * 암호문은 그대로, 알 수 없는 값(손상·모르는 키 버전)도 덮어쓰지 않고 그대로 두고 수만 로그로 남긴다 (PO #317 1번). 몇 번 켜도 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotesEncryptor implements ApplicationRunner {

    private static final String LEGACY = "(%1$s IS NOT NULL AND LENGTH(%1$s) >= 1 AND ASCII(%1$s) = 0)";
    private static final String UNKNOWN = "(%1$s IS NOT NULL AND ASCII(%1$s) <> 0 AND NOT (LENGTH(%1$s) >= 29 AND ASCII(%1$s) = 1))";

    private final JdbcTemplate jdbc;
    private final DataCipher cipher;

    @Override
    public void run(ApplicationArguments args) {
        int count = encryptPlaintext();
        if (count > 0) {
            log.info("일기·메모 원문 {}행을 암호화했습니다", count);
        }
        Integer unknown = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM diaries WHERE " + UNKNOWN.formatted("content") + ") + (SELECT COUNT(*) FROM memos WHERE "
            + UNKNOWN.formatted("title") + " OR " + UNKNOWN.formatted("content") + ")", Integer.class);
        if (unknown != null && unknown > 0) {
            log.error("일기·메모 {}행이 알 수 없는 형식이라 읽을 수 없습니다 — 덮어쓰지 않고 그대로 둡니다", unknown);
        }
    }

    /** @return 다시 저장한 행 수 */
    public int encryptPlaintext() {
        int count = 0;
        for (Map<String, Object> row : jdbc.queryForList("SELECT diary_id, content FROM diaries WHERE " + LEGACY.formatted("content"))) {
            byte[] old = (byte[]) row.get("content");
            count += jdbc.update("UPDATE diaries SET content = ? WHERE diary_id = ? AND content = ?", seal(old), row.get("diary_id"), old);
        }
        List<Map<String, Object>> memos = jdbc.queryForList("SELECT memo_id, title, content FROM memos WHERE "
            + LEGACY.formatted("title") + " OR " + LEGACY.formatted("content"));
        for (Map<String, Object> row : memos) {
            byte[] title = (byte[]) row.get("title");
            byte[] content = (byte[]) row.get("content");
            count += jdbc.update("UPDATE memos SET title = ?, content = ? WHERE memo_id = ? AND title <=> ? AND content <=> ?",
                seal(title), seal(content), row.get("memo_id"), title, content);
        }
        return count;
    }

    private byte[] seal(byte[] stored) {
        if (!DataCipher.isLegacy(stored)) {
            return stored; // 없음·암호문·알 수 없는 값은 그대로
        }
        return cipher.encrypt(cipher.decrypt(stored));
    }
}
