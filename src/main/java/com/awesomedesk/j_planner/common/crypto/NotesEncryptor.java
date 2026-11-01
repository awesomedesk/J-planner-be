package com.awesomedesk.j_planner.common.crypto;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 암호화 전 일기·메모 원문을 서버를 켤 때 암호문 칸으로 옮긴다 (US-41 AC4, D-066: 기존 admin 데이터, 로컬 예시 데이터, 07 11-8).
 * 원문은 원래 칸(content·title), 암호문은 새 칸(*_enc)에 있어서 <b>값의 모양으로 원문인지 추측하지 않는다</b> (PO #317).
 * 옮기는 조건: 원문 칸에 값이 있고 암호문 칸이 비어 있음 → 암호화해 암호문 칸에 쓰고 원문 칸을 비운다 (한 행 한 UPDATE, 그 사이 바뀌었으면 건너뜀).
 * 암호문 칸에 이미 값이 있으면(손상값 포함) 절대 건드리지 않는다. 몇 번 켜도 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotesEncryptor implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final DataCipher cipher;

    @Override
    public void run(ApplicationArguments args) {
        int count = encryptPlaintext();
        if (count > 0) {
            log.info("일기·메모 원문 {}행을 암호문 칸으로 옮겼습니다", count);
        }
        Integer both = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM diaries WHERE content IS NOT NULL AND content_enc IS NOT NULL)"
            + " + (SELECT COUNT(*) FROM memos WHERE (title IS NOT NULL AND title_enc IS NOT NULL) OR (content IS NOT NULL AND content_enc IS NOT NULL))", Integer.class);
        if (both != null && both > 0) {
            log.error("일기·메모 {}행에 원문과 암호문이 함께 있습니다 — 덮어쓰지 않고 그대로 둡니다 (확인 필요)", both);
        }
    }

    /** @return 옮긴 행 수 */
    public int encryptPlaintext() {
        int count = 0;
        for (Map<String, Object> row : jdbc.queryForList("SELECT diary_id, content FROM diaries WHERE content IS NOT NULL AND content_enc IS NULL")) {
            String plain = (String) row.get("content");
            count += jdbc.update("UPDATE diaries SET content_enc = ?, content = NULL WHERE diary_id = ? AND content_enc IS NULL AND content = ?",
                cipher.encrypt(plain), row.get("diary_id"), plain);
        }
        for (Map<String, Object> row : jdbc.queryForList("SELECT memo_id, title, content FROM memos"
            + " WHERE (title IS NOT NULL AND title_enc IS NULL AND (content IS NULL OR content_enc IS NULL))"
            + " OR (content IS NOT NULL AND content_enc IS NULL AND (title IS NULL OR title_enc IS NULL))")) {
            String title = (String) row.get("title");
            String content = (String) row.get("content");
            count += jdbc.update("UPDATE memos SET title_enc = ?, content_enc = ?, title = NULL, content = NULL"
                    + " WHERE memo_id = ? AND title_enc IS NULL AND content_enc IS NULL AND title <=> ? AND content <=> ?",
                seal(title), seal(content), row.get("memo_id"), title, content);
        }
        return count;
    }

    private byte[] seal(String plain) {
        return plain == null ? null : cipher.encrypt(plain);
    }
}
