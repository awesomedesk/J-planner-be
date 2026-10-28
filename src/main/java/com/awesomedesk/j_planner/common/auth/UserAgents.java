package com.awesomedesk.j_planner.common.auth;

/**
 * 로그인 기기 목록에 보일 짧은 이름 (US-37, 08 13-4 'Chrome · macOS'). User-Agent 원문은 보내지 않는다.
 * 순서가 중요하다: Edge·Opera·Samsung 브라우저도 'Chrome'을, Chrome도 'Safari'를 함께 적는다.
 */
public final class UserAgents {

    private UserAgents() {
    }

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "알 수 없는 기기";
        }
        return browser(userAgent) + " · " + os(userAgent);
    }

    static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("Whale/")) {
            return "Whale";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return "브라우저";
    }

    static String os(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "기기";
    }

    /** IP 앞부분만: IPv4 '211.36.*.*', IPv6 '2001:db8:*'. 모르면 빈 문자열 */
    public static String ipPrefix(String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            return parts.length >= 2 ? parts[0] + ":" + parts[1] + ":*" : "*";
        }
        String[] parts = ip.split("\\.");
        return parts.length == 4 ? parts[0] + "." + parts[1] + ".*.*" : "*";
    }
}
