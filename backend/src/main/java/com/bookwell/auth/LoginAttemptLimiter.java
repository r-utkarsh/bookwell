package com.bookwell.auth;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_PER_EMAIL_AND_IP = 5;
    private static final int MAX_PER_EMAIL = 25;
    private static final long WINDOW_MS = 15 * 60_000L;
    private static final int MAX_ENTRIES = 50_000;

    private static final class Window {
        long start;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean isBlocked(String email, String ip) {
        long now = System.currentTimeMillis();
        String safeIp = ip == null ? "" : ip;
        return count("p:" + email + "|" + safeIp, now) >= MAX_PER_EMAIL_AND_IP
                || count("e:" + email, now) >= MAX_PER_EMAIL;
    }

    public void recordFailure(String email, String ip) {
        long now = System.currentTimeMillis();
        String safeIp = ip == null ? "" : ip;
        if (windows.size() >= MAX_ENTRIES) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MS);
            if (windows.size() >= MAX_ENTRIES) {
                windows.clear();
            }
        }
        bump("p:" + email + "|" + safeIp, now);
        bump("e:" + email, now);
    }

    public void recordSuccess(String email, String ip) {
        String safeIp = ip == null ? "" : ip;
        windows.remove("p:" + email + "|" + safeIp);
    }

    private int count(String key, long now) {
        Window w = windows.get(key);
        if (w == null) {
            return 0;
        }
        synchronized (w) {
            return now - w.start >= WINDOW_MS ? 0 : w.count;
        }
    }

    private void bump(String key, long now) {
        Window w = windows.computeIfAbsent(key, k -> {
            Window n = new Window();
            n.start = now;
            return n;
        });
        synchronized (w) {
            if (now - w.start >= WINDOW_MS) {
                w.start = now;
                w.count = 0;
            }
            w.count++;
        }
    }
}
