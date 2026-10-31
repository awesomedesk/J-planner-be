package com.awesomedesk.j_planner.common.net;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * 요청한 사람의 진짜 IP (US-41). 요청 제한·감사 기록·로그인 기기 목록이 쓴다 — {@code getRemoteAddr()}를 직접 쓰지 않는다.
 * <p>
 * 연결 주소가 믿는 프록시(설정 {@code app.network.trusted-proxies}, CIDR 쉼표 목록 — tst·prd는 {@code JP_TRUSTED_PROXIES})일 때만
 * {@code X-Forwarded-For}를 본다. 오른쪽(프록시가 붙인 쪽)부터 믿는 프록시를 건너뛰고 처음 나오는 주소 = 진짜 IP.
 * 클라이언트가 앞쪽에 끼워 넣은 값은 그보다 왼쪽이라 쓰이지 않는다. 믿지 않는 연결의 X-Forwarded-For는 무시.
 * IP 형식이 아닌 값을 만나면 거기서 멈추고 마지막으로 믿은 주소(프록시)를 쓴다.
 */
@Component
public class ClientIp {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIp(@Value("${app.network.trusted-proxies:}") List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream().map(String::strip).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String of(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!trusted(ip)) {
            return ip;
        }
        List<String> hops = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(X_FORWARDED_FOR))) {
            for (String hop : header.split(",")) {
                hops.add(hop.strip());
            }
        }
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!isIp(hop)) {
                return ip;
            }
            ip = hop;
            if (!trusted(hop)) {
                return hop;
            }
        }
        return ip;
    }

    private boolean trusted(String ip) {
        if (!isIp(ip)) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    /** IP 글자 그대로인지 (이름 조회는 하지 않는다) */
    static boolean isIp(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
            return false;
        }
        try {
            InetAddress.ofLiteral(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
