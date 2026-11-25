package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.awesomedesk.j_planner.api.v1.diary.DiaryRepository;
import com.awesomedesk.j_planner.api.v1.memo.MemoRepository;
import com.awesomedesk.j_planner.common.auth.AuthUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US-44 · #388 C-3 · #398 3번: 일기·메모 본문은 오류가 나도 로그·오류 응답에 남지 않는다.
 * <p>
 * 로그 수준은 local 프로필과 같게 맞춘다 (가장 많이 남기는 설정: 전체 DEBUG, SQL DEBUG, 바인딩 값 TRACE).
 * tst(INFO)·prd(ERROR)는 이보다 적게 남기므로 여기서 통과하면 같이 통과한다.
 * 본문 대신 한 번만 쓰는 표식 문자열을 넣고, 응답 본문과 그동안 남은 모든 로그(예외 스택 포함)에서 그 문자열을 찾는다.
 */
@DisplayName("US-44 오류 경로의 일기·메모 본문")
class US44NotesNotInErrorsAcceptanceTest extends AcceptanceTest {

    /** 본문 표식 — JSON에서 따옴표 없이 써도 하나의 토큰이 되게 영문·숫자만 */
    private static final String SECRET = "Zq7xNoteBodyMarker42";

    @Autowired
    private WebApplicationContext context;

    @MockitoSpyBean
    private MemoRepository memoRepository;

    @MockitoSpyBean
    private DiaryRepository diaryRepository;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Map<String, Level> savedLevels = new LinkedHashMap<>();

    @BeforeEach
    void captureLogs() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setLevel(context, Logger.ROOT_LOGGER_NAME, Level.DEBUG);
        setLevel(context, "org.hibernate.SQL", Level.DEBUG);
        setLevel(context, "org.hibernate.orm.jdbc.bind", Level.TRACE);
        logs.setContext(context);
        logs.start();
        context.getLoggerList().forEach(logger -> logger.addAppender(logs));
    }

    @AfterEach
    void restoreLogs() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLoggerList().forEach(logger -> logger.detachAppender(logs));
        logs.stop();
        savedLevels.forEach((name, level) -> context.getLogger(name).setLevel(level));
    }

    @Test
    @DisplayName("JSON 형식 오류(따옴표 없는 값·끊긴 본문)·값 형식 오류면 400이고, 응답·로그에 본문이 없다")
    void malformedBodies() throws Exception {
        String diary = "/api/v1/diaries/" + TODAY;
        assertNoSecret(send(put(diary), "{\"content\": " + SECRET + "}"), 400);
        assertNoSecret(send(put(diary), "{\"content\":\"" + SECRET + "\""), 400);
        assertNoSecret(send(put(diary), "{\"content\":{\"text\":\"" + SECRET + "\"}}"), 400);
        assertNoSecret(send(put(diary), "{\"content\":\"" + SECRET + "\",}"), 400);
        assertNoSecret(send(post("/api/v1/memos"), "{\"title\": " + SECRET + ", \"content\":\"x\"}"), 400);
        assertNoSecret(send(post("/api/v1/memos"), "{\"title\":\"t\",\"content\":[\"" + SECRET + "\"]}"), 400);
        assertNoSecret(send(post("/api/v1/memos"), "[\"" + SECRET + "\"]"), 400);
    }

    @Test
    @DisplayName("본문이 1MiB를 넘으면 413이고, 응답·로그에 본문이 없다")
    void tooLarge() throws Exception {
        String big = SECRET + "a".repeat(1024 * 1024);
        assertNoSecret(send(put("/api/v1/diaries/" + TODAY), "{\"content\":\"" + big + "\"}"), 413);
        assertNoSecret(send(post("/api/v1/memos"), "{\"title\":\"" + SECRET + "\",\"content\":\"" + big + "\"}"), 413);
    }

    @Test
    @DisplayName("검증 오류(빈 메모·빈 일기, 잘못된 날짜)면 400이고, 응답·로그에 본문이 없다")
    void validationErrors() throws Exception {
        assertNoSecret(send(put("/api/v1/diaries/2026-13-40"), "{\"content\":\"" + SECRET + "\"}"), 400);
        assertNoSecret(send(post("/api/v1/memos"), "{\"title\":\"   \",\"content\":\"   \",\"extra\":\"" + SECRET + "\"}"), 400);
        assertNoSecret(send(put("/api/v1/diaries/" + TODAY), "{\"content\":\"  \",\"extra\":\"" + SECRET + "\"}"), 400);
    }

    @Test
    @DisplayName("저장 실패(DB 오류)면 500 INTERNAL_ERROR이고, 예외 스택을 포함한 로그와 응답에 본문이 없다")
    void storageFailure() throws Exception {
        doThrow(new DataAccessResourceFailureException("DB 연결 끊김")).when(diaryRepository).saveAndFlush(any());
        doThrow(new DataAccessResourceFailureException("DB 연결 끊김")).when(memoRepository).saveAndFlush(any());

        // 500은 08의 공통 응답(경로마다 적지 않음)이라 명세 대조 없는 MockMvc로 보낸다 — 로그인·CSRF 기본값은 같게
        MockMvc noContract = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
            .defaultRequest(get("/").cookie(new Cookie("__Host-XSRF-TOKEN", "t")).header("X-XSRF-TOKEN", "t")
                .with(authentication(new AuthUser(ADMIN_ID).toAuthentication())))
            .build();
        assertNoSecret(noContract.perform(put("/api/v1/diaries/" + TODAY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"" + SECRET + "\"}")).andReturn(), 500);
        assertNoSecret(noContract.perform(post("/api/v1/memos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"" + SECRET + "\",\"content\":\"" + SECRET + "\"}")).andReturn(), 500);
        assertThat(logs.list).as("오류 로그는 남는다").anyMatch(e -> e.getLevel() == Level.ERROR && e.getThrowableProxy() != null);
    }

    @Test
    @DisplayName("정상 저장·수정·조회도 SQL·바인딩 값 로그에 본문이 없다 (DB에는 암호문만 간다)")
    void normalFlow() throws Exception {
        assertNoSecret(send(put("/api/v1/diaries/" + TODAY), "{\"content\":\"" + SECRET + "\"}"), 201);
        long memo = idOf(postJson("/api/v1/memos", "{\"title\":\"" + SECRET + "\",\"content\":\"" + SECRET + "\"}"));
        assertNoSecret(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/memos/" + memo)
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + SECRET + " 고침\"}")).andReturn(), 200, false);
        getJson("/api/v1/memos/" + memo);
        getJson("/api/v1/diaries/" + TODAY);
        assertThat(logs.list).as("SQL 로그는 남는다 (검사가 실제로 로그를 보고 있음)").anyMatch(e -> e.getLoggerName().startsWith("org.hibernate.SQL"));
        assertThat(leakedLogs()).as("본문이 남은 로그").isEmpty();
    }

    // ------------------------------------------------------------------ 준비

    private MvcResult send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8))).andReturn();
    }

    private void assertNoSecret(MvcResult result, int status) throws Exception {
        assertNoSecret(result, status, true);
    }

    private void assertNoSecret(MvcResult result, int status, boolean responseMustNotEcho) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        if (responseMustNotEcho && status >= 400) {
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).as("오류 응답").doesNotContain(SECRET);
        }
        assertThat(leakedLogs()).as("본문이 남은 로그").isEmpty();
    }

    /** 표식이 들어간 로그 줄 (로거 이름: 내용) */
    private java.util.List<String> leakedLogs() {
        return logs.list.stream().map(e -> e.getLoggerName() + " [" + e.getLevel() + "]: " + e.getFormattedMessage()
                + (e.getThrowableProxy() == null ? "" : "\n" + ThrowableProxyUtil.asString(e.getThrowableProxy())))
            .filter(line -> line.contains(SECRET)).distinct().toList();
    }

    private void setLevel(LoggerContext context, String name, Level level) {
        Logger logger = context.getLogger(name);
        savedLevels.put(name, logger.getLevel());
        logger.setLevel(level);
    }
}
