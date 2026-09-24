package com.awesomedesk.j_planner.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 Problem Details(RFC 9457, {@code application/problem+json})로 돌려준다 (D-031).
 *
 * <pre>
 * { "type": "about:blank", "title": "Conflict", "status": 409,
 *   "detail": "같은 이름의 카테고리가 이미 있습니다: 공부", "instance": "/api/v1/categories",
 *   "code": "CATEGORY_NAME_DUPLICATED", "errors": [] }
 * </pre>
 *
 * <ul>
 *   <li>{@link ApiException}: 서비스에서 던진 오류 → 그 {@link ErrorCode}</li>
 *   <li>Spring MVC 오류(필수 파라미터 없음, JSON 형식 오류, 405, 415, 404 등) → 부모 클래스가 만든 ProblemDetail에
 *       {@code code}·{@code errors}를 붙인다</li>
 *   <li>그 외 모든 예외 → 500 {@code INTERNAL_ERROR} (내부 정보는 응답에 넣지 않고 로그로만 남긴다)</li>
 * </ul>
 * 규칙: j-planner-product/08-api-design.md 2-6절
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE = "code";
    static final String ERRORS = "errors";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("API error: {}", code, ex);
        } else {
            log.debug("API error: {} - {}", code, ex.getMessage());
        }
        ProblemDetail problem = problem(code.getStatus(), code, ex.getMessage(), ex.getErrors(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(problem);
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJpaEntityNotFound(
        jakarta.persistence.EntityNotFoundException ex, HttpServletRequest request) {
        log.debug("Entity not found: {}", ex.getMessage());
        ErrorCode code = ErrorCode.NOT_FOUND;
        ProblemDetail problem = problem(code.getStatus(), code, code.getDefaultDetail(), List.of(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = problem(code.getStatus(), code, code.getDefaultDetail(), List.of(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(problem);
    }

    /** {@code @Valid} 요청 본문 검증 실패 → 400 VALIDATION_FAILED + 필드별 errors */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()));
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            errors.add(new FieldErrorDetail(oe.getObjectName(), oe.getDefaultMessage()));
        }
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        ProblemDetail problem = problem(status, code, code.getDefaultDetail(), errors, requestUri(request));
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * 부모 클래스가 처리하는 Spring MVC 오류 전부가 거쳐 가는 곳.
     * 만들어진 ProblemDetail에 code·errors·instance를 채우고 detail을 한국어로 바꾼다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Map<String, Object> props = problem.getProperties();
            if (props == null || !props.containsKey(CODE)) {
                ErrorCode code = codeFor(ex, statusCode);
                if (statusCode.is5xxServerError()) {
                    log.error("MVC error: {}", ex.getClass().getSimpleName(), ex);
                } else {
                    log.debug("MVC error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
                }
                problem.setDetail(detailFor(ex, code));
                problem.setProperty(CODE, code.name());
                problem.setProperty(ERRORS, errorsFor(ex));
            }
            if (problem.getInstance() == null) {
                String uri = requestUri(request);
                if (uri != null) {
                    problem.setInstance(URI.create(uri));
                }
            }
        }
        return response;
    }

    // ------------------------------------------------------------------ helpers

    private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MissingServletRequestParameterException || ex instanceof TypeMismatchException) {
            return ErrorCode.INVALID_QUERY;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.UNSUPPORTED_REQUEST;
    }

    private static String detailFor(Exception ex, ErrorCode code) {
        if (ex instanceof HttpMessageNotReadableException) {
            return "요청 본문(JSON)을 읽을 수 없습니다. 형식을 확인하세요.";
        }
        return code.getDefaultDetail();
    }

    private static List<FieldErrorDetail> errorsFor(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException e) {
            return List.of(new FieldErrorDetail(e.getParameterName(), "필수 조회 조건입니다."));
        }
        if (ex instanceof TypeMismatchException e && e.getPropertyName() != null) {
            return List.of(new FieldErrorDetail(e.getPropertyName(), "형식이 올바르지 않습니다."));
        }
        return List.of();
    }

    private static ProblemDetail problem(HttpStatusCode status, ErrorCode code, String detail,
        List<FieldErrorDetail> errors, @Nullable String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code.name());
        problem.setProperty(ERRORS, errors);
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        return problem;
    }

    @Nullable
    private static String requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
