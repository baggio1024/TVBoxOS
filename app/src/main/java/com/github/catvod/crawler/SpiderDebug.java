package com.github.catvod.crawler;

public class SpiderDebug {
    public static void log(Throwable th) {
        if (th == null) return;
        try {
            String msg = th.getMessage();
            if (msg != null && (msg.contains("127.0.0.1") || msg.contains("localhost") || msg.contains("Connection refused") || msg.contains("ConnectException"))) {
                // 彻底压制所有涉及本地回环地址的连接报错日志，防止扫描端口产生的日志污染
                return;
            }
            android.util.Log.d("SpiderLog", msg != null ? msg : "unknown error", th);
        } catch (Throwable ignored) {
        }
    }

    public static void log(String msg) {
        if (msg == null) return;
        try {
            // 增强过滤：拦截所有涉及本地 127.0.0.1 端口扫描的字符串日志
            if (msg.contains("127.0.0.1") || msg.contains("localhost") || msg.contains("Failed to connect") || msg.contains("Connection refused")) {
                return;
            }
            android.util.Log.d("SpiderLog", msg);
        } catch (Throwable ignored) {
        }
    }

    public static String ec(int i) {
        return "";
    }
}
