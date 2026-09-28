package com.awesomedesk.j_planner.common.aop.logger;

import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import tools.jackson.databind.JsonNode;

/**
 * 컨트롤러·서비스 호출 로그 (debug/trace).
 * 일기·메모 같은 개인 내용이 로그에 남지 않도록 값 대신 모양만 적는다 ({@link #describe}).
 */
@Aspect
@Slf4j
public class LoggerAspect {

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("execution(* *..*Controller.*(..))")
    public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable {
        return around(joinPoint, true);
    }

    @Around("execution(* *..*Service.*(..))")
    public Object doServiceLog(ProceedingJoinPoint joinPoint) throws Throwable {
        return around(joinPoint, false);
    }

    private Object around(ProceedingJoinPoint joinPoint, boolean controller) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String type = signature.getDeclaringType().getSimpleName();
        String method = signature.getName();
        boolean enabled = controller ? log.isDebugEnabled() : log.isTraceEnabled();
        if (enabled) {
            write(controller, "{} {} - input : {}", type, method, formatInput(signature.getMethod(), joinPoint.getArgs()));
        }
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            log.warn("{} {} - error: {}", type, method, t.toString());
            throw t;
        }
        if (enabled) {
            write(controller, "{} {} - output : {}", type, method, describe(result));
        }
        return result;
    }

    private static void write(boolean controller, String format, Object... args) {
        if (controller) {
            log.debug(format, args);
        } else {
            log.trace(format, args);
        }
    }

    private String formatInput(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return "-";
        }
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Map<String, String> mapped = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = names != null && names.length == args.length ? names[i] : "arg" + i;
            mapped.put(name, describe(args[i]));
        }
        return mapped.toString();
    }

    /**
     * 로그에 적을 값: id·숫자·날짜·짧은 조회 값은 그대로, JSON 본문은 필드 이름만, 목록은 개수만, 그 밖의 객체는 타입 이름만.
     */
    static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Temporal || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof CharSequence s) {
            return s.length() <= 30 ? s.toString() : "String(length=" + s.length() + ")";
        }
        if (value instanceof JsonNode node) {
            return "JSON" + node.propertyNames();
        }
        if (value instanceof List<?> list) {
            return "List(size=" + list.size() + ")";
        }
        if (value instanceof Collection<?> c) {
            return "Collection(size=" + c.size() + ")";
        }
        return "<" + value.getClass().getSimpleName() + ">";
    }
}
