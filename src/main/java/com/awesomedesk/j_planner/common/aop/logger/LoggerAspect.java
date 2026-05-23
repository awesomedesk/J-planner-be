package com.awesomedesk.j_planner.common.aop.logger;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Slf4j
public class LoggerAspect {
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("execution(* *..*Controller.*(..))")
    public Object doLog(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String controller = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String input = formatInput(signature.getMethod(), joinPoint.getArgs());

        log.debug("{} {} - input : {}", controller, methodName, input);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            log.warn("{} {} - error", controller, methodName, t);
            throw t;
        }

        // "output" logging for controller only. (Avoid adding this to service to reduce noise.)
        log.debug("{} {} - output : {}", controller, methodName, result);
        return result;
    }

    @Around("execution(* *..*Service.*(..))")
    public Object doServiceLog(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String service = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String input = formatInput(signature.getMethod(), joinPoint.getArgs());

        log.trace("{} {} - input : {}", service, methodName, input);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            log.warn("{} {} - error", service, methodName, t);
            throw t;
        }

        log.trace("{} {} - output : {}", service, methodName, result);
        return result;
    }

    private String formatInput(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return "-";
        }

        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames == null || paramNames.length != args.length) {
            // Parameter names may be unavailable depending on compiler flags/debug info.
            return Arrays.toString(args);
        }

        Map<String, Object> mapped = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            mapped.put(paramNames[i], args[i]);
        }
        return mapped.toString();
    }
}
