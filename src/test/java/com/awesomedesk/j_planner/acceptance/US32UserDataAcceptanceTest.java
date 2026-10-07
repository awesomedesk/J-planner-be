package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * US-32 모든 데이터가 사용자별로 저장되고, 기존 1인 데이터는 admin 계정으로 옮겨진다 (SEC-01·02·03, D-060·D-061).
 * 설계: 07-db-design.md 11절, 08-api-design.md 13-2절.
 * <p>
 * 로그인 API(US-34) 전이라 '로그인'은 요청에 회원을 직접 붙여 흉내 낸다 (기본 admin, {@code as(id)}, {@code anonymous()}).
 * AC4(BE가 DB·API·로그인 유지 방식 제안 → 대표님 결정)는 문서로 끝났다 (12-m5-be-proposal.md, D-061) — 테스트 없음.
 */
@DisplayName("US-32 사용자별 데이터")
class US32UserDataAcceptanceTest extends AcceptanceTest {

    private static final String WEEK_FROM = "2026-09-20";
    private static final String WEEK_TO = "2026-09-26";

    private long other;

    @BeforeEach
    void createOtherUser() {
        other = createUser("other@test.local");
    }

    // ------------------------------------------------------------------ AC1

    @Test
    @DisplayName("AC1: 새 회원도 자기 기본 데이터(미지정 카테고리·설정·사이드바 4개)를 갖는다")
    void newUserHasOwnDefaults() throws Exception {
        mvc.perform(get("/api/v1/categories").with(as(other)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("미지정"))
            .andExpect(jsonPath("$[0].isDefault").value(true))
            .andExpect(jsonPath("$[0].id").value((int) defaultCategoryId(other)));

        mvc.perform(get("/api/v1/settings").with(as(other)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weekStartDay").value("SUN"))
            .andExpect(jsonPath("$.darkMode").value(false))
            .andExpect(jsonPath("$.sidebarItems.length()").value(4))
            .andExpect(jsonPath("$.sidebarItems[0].type").value("TODO"));
    }

    @Test
    @DisplayName("AC1: 유일 규칙(카테고리 이름·일기 날짜·설정 1행)은 사용자별이다")
    void uniqueRulesArePerUser() throws Exception {
        createCategory("공부");
        send(post("/api/v1/categories"), "{\"name\":\"공부\"}", as(other)).andExpect(status().isCreated());

        putJson("/api/v1/diaries/" + TODAY, "{\"content\":\"admin 일기\"}").andExpect(status().isCreated());
        send(put("/api/v1/diaries/" + TODAY), "{\"content\":\"다른 회원 일기\"}", as(other)).andExpect(status().isCreated());
        getJson("/api/v1/diaries/" + TODAY).andExpect(jsonPath("$.content").value("admin 일기"));

        send(patch("/api/v1/settings"), "{\"darkMode\":true}", as(other)).andExpect(jsonPath("$.darkMode").value(true));
        getJson("/api/v1/settings").andExpect(jsonPath("$.darkMode").value(false));
    }

    // ------------------------------------------------------------------ AC2

    @Test
    @DisplayName("AC2: 목록 조회는 로그인한 사람의 데이터만 돌려준다")
    void listsShowOnlyMyData() throws Exception {
        AdminData a = createAdminData();
        completeAdminTodoToday(a.todo());

        assertEmpty(other, "/api/v1/schedules?from=" + WEEK_FROM + "&to=" + WEEK_TO);
        assertEmpty(other, "/api/v1/todos?date=" + TODAY);
        assertEmpty(other, "/api/v1/todos?from=" + WEEK_FROM + "&to=" + WEEK_TO);
        assertEmpty(other, "/api/v1/todos?completedOn=" + TODAY);
        assertEmpty(other, "/api/v1/ddays");
        assertEmpty(other, "/api/v1/dday-marks?from=" + WEEK_FROM + "&to=" + WEEK_TO);
        assertEmpty(other, "/api/v1/diaries?from=" + WEEK_FROM + "&to=" + WEEK_TO);
        assertEmpty(other, "/api/v1/memos");
        mvc.perform(get("/api/v1/categories").with(as(other)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].scheduleCount").value(0))
            .andExpect(jsonPath("$[0].todoCount").value(0));

        // admin은 그대로 보인다
        assertThat(ids(getJson("/api/v1/schedules?from=" + WEEK_FROM + "&to=" + WEEK_TO))).containsExactly((int) a.schedule());
        assertThat(ids(getJson("/api/v1/memos"))).containsExactly((int) a.memo());
    }

    @Test
    @DisplayName("AC2: 다른 계정의 id로 읽기·수정·삭제·순서 이동을 하면 404이고, 원래 데이터는 그대로다")
    void otherUsersIdsAreNotFound() throws Exception {
        AdminData a = createAdminData();

        List<MockHttpServletRequestBuilder> requests = List.of(
            patch("/api/v1/categories/" + a.category()), delete("/api/v1/categories/" + a.category()),
            put("/api/v1/categories/" + a.category() + "/position"),
            get("/api/v1/schedules/" + a.schedule()), patch("/api/v1/schedules/" + a.schedule()),
            delete("/api/v1/schedules/" + a.schedule()),
            get("/api/v1/todos/" + a.todo()), patch("/api/v1/todos/" + a.todo()), delete("/api/v1/todos/" + a.todo()),
            put("/api/v1/todos/" + a.todo() + "/position"),
            get("/api/v1/ddays/" + a.dday()), patch("/api/v1/ddays/" + a.dday()), delete("/api/v1/ddays/" + a.dday()),
            put("/api/v1/ddays/" + a.dday() + "/position"),
            get("/api/v1/memos/" + a.memo()), patch("/api/v1/memos/" + a.memo()), delete("/api/v1/memos/" + a.memo()),
            get("/api/v1/diaries/" + TODAY), delete("/api/v1/diaries/" + TODAY));
        for (MockHttpServletRequestBuilder request : requests) {
            String body = request.buildRequest(null).getMethod().equals("PUT") ? "{\"afterId\":null}" : "{\"title\":\"바꿈\",\"name\":\"바꿈\"}";
            send(request, body, as(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        // admin 데이터는 그대로
        getJson("/api/v1/schedules/" + a.schedule()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("회의"));
        getJson("/api/v1/todos/" + a.todo()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("보고서"));
        getJson("/api/v1/ddays/" + a.dday()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("시험"));
        getJson("/api/v1/memos/" + a.memo()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("장보기"));
        getJson("/api/v1/diaries/" + TODAY).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("admin 일기"));
        assertThat((List<String>) read(getJson("/api/v1/categories"), "$[*].name")).containsExactly("미지정", "공부");
    }

    @Test
    @DisplayName("AC2: 다른 계정의 카테고리를 일정·Todo에 넣거나, 다른 계정의 항목 뒤로 옮기면 400이다")
    void otherUsersReferencesAreRejected() throws Exception {
        AdminData a = createAdminData();

        send(post("/api/v1/schedules"), "{\"title\":\"남의 카테고리\",\"allDay\":false,\"start\":\"2026-09-25T10:00:00\","
            + "\"end\":\"2026-09-25T11:00:00\",\"categoryId\":" + a.category() + "}", as(other))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        send(post("/api/v1/todos"), "{\"title\":\"남의 카테고리\",\"type\":\"DAY\",\"startDate\":\"" + TODAY + "\",\"endDate\":\""
            + TODAY + "\",\"categoryId\":" + a.category() + "}", as(other))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        long myTodo = idOf(send(post("/api/v1/todos"), "{\"title\":\"내 Todo\",\"type\":\"DAY\",\"startDate\":\"" + TODAY
            + "\",\"endDate\":\"" + TODAY + "\"}", as(other)));
        send(put("/api/v1/todos/" + myTodo + "/position"), "{\"afterId\":" + a.todo() + "}", as(other))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("afterId"));
    }

    @Test
    @DisplayName("AC2: 요청 본문의 userId는 받지 않는다 — 항상 로그인한 사람의 데이터로 저장된다")
    void userIdInBodyIsIgnored() throws Exception {
        send(post("/api/v1/memos"), "{\"title\":\"몰래\",\"content\":\"admin 것으로?\",\"userId\":" + ADMIN_ID + "}", as(other))
            .andExpect(status().isCreated());

        assertThat(ids(getJson("/api/v1/memos"))).isEmpty();
        assertThat(ids(mvc.perform(get("/api/v1/memos").with(as(other))))).hasSize(1);
    }

    @Test
    @DisplayName("AC2: 로그인하지 않으면 모든 데이터 API가 401 UNAUTHENTICATED다")
    void anonymousIsUnauthorized() throws Exception {
        for (Endpoint e : DATA_ENDPOINTS) {
            send(e.request(), "{}", anonymous())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        getJson("/api/v1/health").andExpect(status().isOk()); // 상태 확인은 로그인 없이
        mvc.perform(get("/api/v1/health").with(anonymous())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC2: 위 401 검사는 명세(08-openapi.yaml)의 모든 데이터 API를 빠짐없이 덮는다")
    void endpointListCoversSpec() throws Exception {
        Set<String> tested = new TreeSet<>();
        DATA_ENDPOINTS.forEach(e -> tested.add(e.method() + " " + e.path()));
        assertThat(tested).isEqualTo(specDataOperations());
    }

    // ------------------------------------------------------------------ AC3

    @Test
    @DisplayName("AC3: 마이그레이션이 admin 계정(설정한 이메일, 일반 회원)을 만들고 기존 데이터를 모두 연결한다")
    void migrationMovesExistingDataToAdmin(@Value("${spring.datasource.url}") String url,
                                           @Value("${spring.datasource.username}") String username,
                                           @Value("${spring.datasource.password}") String password) {
        String migrationUrl = url.replaceFirst("/jp_test\\?", "/jp_test_migration?");
        assertThat(migrationUrl).contains("jp_test_migration");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(migrationUrl, username, password);
        JdbcTemplate db = new JdbcTemplate(dataSource);
        Flyway v1 = flyway(dataSource, "1");
        v1.clean();
        v1.migrate();

        // V1 시절(1인 사용) 데이터
        long category = insertV1Data(db);

        flyway(dataSource, "latest").migrate();

        Map<String, Object> admin = db.queryForMap("SELECT user_id, email, name, status FROM users");
        assertThat(admin).containsEntry("email", "owner@test.local").containsEntry("status", "ACTIVE");
        long adminId = ((Number) admin.get("user_id")).longValue();
        for (String table : List.of("categories", "calendars", "todos", "ddays", "diaries", "memos", "user_settings", "sidebar_items")) {
            assertThat(db.queryForList("SELECT DISTINCT user_id FROM " + table, Long.class))
                .as(table).containsExactly(adminId);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT category_id FROM todos", Long.class)).isEqualTo(category);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sidebar_items", Integer.class)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 준비

    /** 로그인이 필요한 데이터 API 전부 (명세와 같은지 endpointListCoversSpec이 확인) */
    private static final List<Endpoint> DATA_ENDPOINTS = List.of(
        new Endpoint(HttpMethod.GET, "/categories", "/categories"),
        new Endpoint(HttpMethod.POST, "/categories", "/categories"),
        new Endpoint(HttpMethod.PATCH, "/categories/{id}", "/categories/1"),
        new Endpoint(HttpMethod.DELETE, "/categories/{id}", "/categories/1"),
        new Endpoint(HttpMethod.PUT, "/categories/{id}/position", "/categories/1/position"),
        new Endpoint(HttpMethod.GET, "/schedules", "/schedules?from=" + WEEK_FROM + "&to=" + WEEK_TO),
        new Endpoint(HttpMethod.POST, "/schedules", "/schedules"),
        new Endpoint(HttpMethod.GET, "/schedules/{id}", "/schedules/1"),
        new Endpoint(HttpMethod.PATCH, "/schedules/{id}", "/schedules/1"),
        new Endpoint(HttpMethod.DELETE, "/schedules/{id}", "/schedules/1"),
        new Endpoint(HttpMethod.GET, "/todos", "/todos?date=" + TODAY),
        new Endpoint(HttpMethod.POST, "/todos", "/todos"),
        new Endpoint(HttpMethod.GET, "/todos/{id}", "/todos/1"),
        new Endpoint(HttpMethod.PATCH, "/todos/{id}", "/todos/1"),
        new Endpoint(HttpMethod.DELETE, "/todos/{id}", "/todos/1"),
        new Endpoint(HttpMethod.PUT, "/todos/{id}/position", "/todos/1/position"),
        new Endpoint(HttpMethod.GET, "/ddays", "/ddays"),
        new Endpoint(HttpMethod.POST, "/ddays", "/ddays"),
        new Endpoint(HttpMethod.GET, "/ddays/{id}", "/ddays/1"),
        new Endpoint(HttpMethod.PATCH, "/ddays/{id}", "/ddays/1"),
        new Endpoint(HttpMethod.DELETE, "/ddays/{id}", "/ddays/1"),
        new Endpoint(HttpMethod.PUT, "/ddays/{id}/position", "/ddays/1/position"),
        new Endpoint(HttpMethod.GET, "/dday-marks", "/dday-marks?from=" + WEEK_FROM + "&to=" + WEEK_TO),
        new Endpoint(HttpMethod.GET, "/diaries", "/diaries?from=" + WEEK_FROM + "&to=" + WEEK_TO),
        new Endpoint(HttpMethod.GET, "/diaries/{date}", "/diaries/" + TODAY),
        new Endpoint(HttpMethod.PUT, "/diaries/{date}", "/diaries/" + TODAY),
        new Endpoint(HttpMethod.DELETE, "/diaries/{date}", "/diaries/" + TODAY),
        new Endpoint(HttpMethod.GET, "/memos", "/memos"),
        new Endpoint(HttpMethod.POST, "/memos", "/memos"),
        new Endpoint(HttpMethod.GET, "/memos/{id}", "/memos/1"),
        new Endpoint(HttpMethod.PATCH, "/memos/{id}", "/memos/1"),
        new Endpoint(HttpMethod.DELETE, "/memos/{id}", "/memos/1"),
        new Endpoint(HttpMethod.GET, "/settings", "/settings"),
        new Endpoint(HttpMethod.PATCH, "/settings", "/settings"));

    private record Endpoint(HttpMethod method, String path, String url) {
        MockHttpServletRequestBuilder request() {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, "/api/v1" + url);
        }
    }

    /** 명세의 데이터 API ("GET /todos" …). 상태 확인·인증(/auth)·내 계정(/me)은 뺀다 */
    private static Set<String> specDataOperations() throws Exception {
        String spec;
        try (InputStream in = new ClassPathResource("static/openapi.yaml").getInputStream()) {
            spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String paths = spec.substring(spec.indexOf("\npaths:"), spec.indexOf("\ncomponents:"));
        Set<String> result = new TreeSet<>();
        String current = null;
        Matcher m = Pattern.compile("(?m)^  (/\\S+):$|^    (get|post|put|patch|delete):$").matcher(paths);
        while (m.find()) {
            if (m.group(1) != null) {
                current = m.group(1);
            } else if (!isPublicOrAccount(current)) {
                result.add(m.group(2).toUpperCase() + " " + current);
            }
        }
        return result;
    }

    private static boolean isPublicOrAccount(String path) {
        return path.equals("/health") || path.startsWith("/auth/") || path.equals("/me") || path.startsWith("/me/");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json, RequestPostProcessor user) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json).with(user));
    }

    private void assertEmpty(long userId, String url) throws Exception {
        mvc.perform(get(url).with(as(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    private record AdminData(long category, long schedule, long todo, long dday, long memo) {
    }

    private AdminData createAdminData() throws Exception {
        long category = createCategory("공부");
        long schedule = createSchedule("회의", "2026-09-25T10:00:00", "2026-09-25T11:00:00", category);
        long todo = createTodo("{\"title\":\"보고서\",\"type\":\"DAY\",\"startDate\":\"" + TODAY + "\",\"endDate\":\"" + TODAY
            + "\",\"categoryId\":" + category + "}");
        long dday = idOf(postJson("/api/v1/ddays", "{\"title\":\"시험\",\"targetDate\":\"2026-09-26\",\"countType\":\"COUNTDOWN\","
            + "\"display\":{\"lastDays\":{\"enabled\":true,\"days\":7}}}"));
        long memo = idOf(postJson("/api/v1/memos", "{\"title\":\"장보기\",\"content\":\"우유\"}"));
        putJson("/api/v1/diaries/" + TODAY, "{\"content\":\"admin 일기\"}").andExpect(status().isCreated());
        return new AdminData(category, schedule, todo, dday, memo);
    }

    private void completeAdminTodoToday(long todo) throws Exception {
        patchJson("/api/v1/todos/" + todo, "{\"completed\":true}").andExpect(status().isOk());
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .placeholders(Map.of("admin_email", "Owner@Test.local"))
            .cleanDisabled(false)
            .target(target)
            .load();
    }

    /** V1 테이블 모양 그대로 넣는다 (user_id 없음). 기본 데이터(미지정·설정·사이드바)는 V1이 넣었다 */
    private static long insertV1Data(JdbcTemplate db) {
        db.update("INSERT INTO categories (name, color, sort_order) VALUES ('공부', '#2F62A8', 1)");
        long category = db.queryForObject("SELECT category_id FROM categories WHERE name = '공부'", Long.class);
        db.update("INSERT INTO calendars (category_id, title, start_date_time, end_date_time) VALUES (?, '회의', '2026-09-25 10:00:00', '2026-09-25 11:00:00')", category);
        db.update("INSERT INTO todos (category_id, title, start_date, end_date) VALUES (?, '보고서', '2026-09-25', '2026-09-25')", category);
        db.update("INSERT INTO ddays (title, target_date) VALUES ('시험', '2026-10-01')");
        db.update("INSERT INTO diaries (diary_date, content) VALUES ('2026-09-25', '일기')");
        db.update("INSERT INTO memos (title, content) VALUES ('장보기', '우유')");
        return category;
    }

}
