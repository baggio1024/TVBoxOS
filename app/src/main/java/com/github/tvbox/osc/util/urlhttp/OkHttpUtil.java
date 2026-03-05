package com.github.tvbox.osc.util.urlhttp;

import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.UA;
import com.lzy.okgo.OkGo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class OkHttpUtil {

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";

    // 终极兜底：如果全局Client没好，临时创建一个，绝不返回null
    private static OkHttpClient backupClient = null;

    private static OkHttpClient safeGetClient(OkHttpClient client) {
        if (client != null) return client;
        
        // 尝试从Helper获取
        client = OkGoHelper.getDefaultClient();
        if (client != null) return client;

        // 如果获取不到（初始化中），使用临时兜底Client
        if (backupClient == null) {
            synchronized (OkHttpUtil.class) {
                if (backupClient == null) {
                    backupClient = new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return backupClient;
    }

    public static String string(OkHttpClient client, String url, String tag, Map<String, String> paramsMap, Map<String, String> headerMap, Map<String, List<String>> respHeaderMap) {
        try {
            client = safeGetClient(client);
            if (url == null || url.isEmpty()) return "";

            OKCallBack<String> stringCallback = new OKCallBack<String>() {
                @Override
                public String onParseResponse(Call call, Response response) {
                    try {
                        if (respHeaderMap != null) {
                            respHeaderMap.clear();
                            respHeaderMap.putAll(response.headers().toMultimap());
                        }
                        return (response.body() != null) ? response.body().string() : "";
                    } catch (IOException e) {
                        return "";
                    }
                }
                @Override public void onFailure(Call call, Exception e) { setResult(""); }
                @Override public void onResponse(String response) {}
            };
            
            OKRequest req = new OKRequest(METHOD_GET, url, paramsMap, headerMap, stringCallback);
            req.setTag(tag);
            req.execute(client);
            return stringCallback.getResult();
        } catch (Throwable th) {
            LOG.e("OkHttpUtil safe-string error: " + th.getMessage());
            return "";
        }
    }

    public static String stringNoRedirect(String url, Map<String, String> headerMap, Map<String, List<String>> respHeaderMap) {
        return string(OkGoHelper.getNoRedirectClient(), url, null, null, headerMap, respHeaderMap);
    }

    public static String string(String url, Map<String, String> headerMap, Map<String, List<String>> respHeaderMap) {
        return string(OkGoHelper.getDefaultClient(), url, null, null, headerMap, respHeaderMap);
    }

    public static String string(String url, Map<String, String> headerMap) {
        return string(OkGoHelper.getDefaultClient(), url, null, null, headerMap, null);
    }

    public static String string(String url, String tag, Map<String, String> headerMap) {
        return string(OkGoHelper.getDefaultClient(), url, tag, null, headerMap, null);
    }

    public static void get(OkHttpClient client, String url, OKCallBack callBack) {
        get(client, url, null, null, callBack);
    }

    public static void get(OkHttpClient client, String url, Map<String, String> paramsMap, OKCallBack callBack) {
        get(client, url, paramsMap, null, callBack);
    }

    public static void get(OkHttpClient client, String url, Map<String, String> paramsMap, Map<String, String> headerMap, OKCallBack callBack) {
        try {
            client = safeGetClient(client);
            if (url != null && !url.isEmpty()) {
                new OKRequest(METHOD_GET, url, paramsMap, headerMap, callBack).execute(client);
            }
        } catch (Throwable th) {
            if (callBack != null) callBack.onFailure(null, new Exception(th));
        }
    }

    public static void post(OkHttpClient client, String url, OKCallBack callBack) {
        post(client, url, null, callBack);
    }

    public static void post(OkHttpClient client, String url, Map<String, String> paramsMap, OKCallBack callBack) {
        post(client, url, paramsMap, null, callBack);
    }

    public static void post(OkHttpClient client, String url, Map<String, String> paramsMap, Map<String, String> headerMap, OKCallBack callBack) {
        try {
            client = safeGetClient(client);
            if (url != null && !url.isEmpty()) {
                new OKRequest(METHOD_POST, url, paramsMap, headerMap, callBack).execute(client);
            }
        } catch (Throwable ignored) {}
    }

    public static void postJson(OkHttpClient client, String url, String jsonStr, OKCallBack callBack) {
        postJson(client, url, jsonStr, null, callBack);
    }

    public static void postJson(OkHttpClient client, String url, String jsonStr, Map<String, String> headerMap, OKCallBack callBack) {
        try {
            client = safeGetClient(client);
            if (url != null && !url.isEmpty()) {
                new OKRequest(METHOD_POST, url, jsonStr, headerMap, callBack).execute(client);
            }
        } catch (Throwable ignored) {}
    }

    public static String get(String str) {
        try {
            if (str == null || str.isEmpty()) return "";
            return OkGo.<String>get(str).headers("User-Agent", UA.random()).execute().body().string();
        } catch (Throwable e) {
            return "";
        }
    }

    public static void cancel(OkHttpClient client, Object tag) {
        if (client == null || tag == null) return;
        try {
            for (Call call : client.dispatcher().queuedCalls()) { if (tag.equals(call.request().tag())) call.cancel(); }
            for (Call call : client.dispatcher().runningCalls()) { if (tag.equals(call.request().tag())) call.cancel(); }
        } catch (Throwable ignored) {}
    }

    public static void cancel(Object tag) { cancel(OkGoHelper.getDefaultClient(), tag); }
    public static void cancelAll() { cancelAll(OkGoHelper.getDefaultClient()); }

    public static void cancelAll(OkHttpClient client) {
        if (client == null) return;
        try {
            for (Call call : client.dispatcher().queuedCalls()) call.cancel();
            for (Call call : client.dispatcher().runningCalls()) call.cancel();
        } catch (Throwable ignored) {}
    }

    public static String getRedirectLocation(Map<String, List<String>> headers) {
        if (headers == null) return null;
        if (headers.containsKey("location")) return headers.get("location").get(0);
        if (headers.containsKey("Location")) return headers.get("Location").get(0);
        return null;
    }
}
