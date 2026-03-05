package com.github.tvbox.osc.util;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.picasso.MyOkhttpDownLoader;
import com.github.tvbox.osc.util.SSL.SSLSocketFactoryCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.Cache;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 10000;
    private static final String dnsConfigJson = "[{\"name\": \"腾讯\", \"url\": \"https://doh.pub/dns-query\"},{\"name\": \"阿里\", \"url\": \"https://dns.alidns.com/dns-query\"},{\"name\": \"360\", \"url\": \"https://doh.360.cn/dns-query\"}]";
    
    static OkHttpClient ItvClient = null;
    public static DnsOverHttps dnsOverHttps = null;
    public static ArrayList<String> dnsHttpsList = new ArrayList<>();
    public static Map<String, String> myHosts = null;
    private static volatile OkHttpClient defaultClient = null;
    private static volatile OkHttpClient noRedirectClient = null;

    private static boolean isHawkReady() {
        try {
            return Hawk.isBuilt();
        } catch (Throwable th) {
            return false;
        }
    }

    public static OkHttpClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (OkGoHelper.class) {
                if (defaultClient == null) init();
            }
        }
        return defaultClient;
    }

    public static OkHttpClient getNoRedirectClient() {
        if (noRedirectClient == null) {
            synchronized (OkGoHelper.class) {
                if (noRedirectClient == null) init();
            }
        }
        return noRedirectClient;
    }

    public static synchronized void init() {
        if (defaultClient != null) return;
        initDnsOverHttps();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkGo");
        loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
        builder.addInterceptor(loggingInterceptor);
        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        if (dnsOverHttps != null) builder.dns(dnsOverHttps);
        try { setOkHttpSsl(builder); } catch (Throwable th) { th.printStackTrace(); }
        HttpHeaders.setUserAgent(Version.userAgent());
        OkHttpClient okHttpClient = builder.build();
        OkGo.getInstance().setOkHttpClient(okHttpClient);
        defaultClient = okHttpClient;
        builder.followRedirects(false);
        builder.followSslRedirects(false);
        noRedirectClient = builder.build();
        initExoOkHttpClient();
        initPicasso(okHttpClient);
    }

    public static void setDnsList() {
        dnsHttpsList.clear();
        dnsHttpsList.add("关闭");
        try {
            String json = Hawk.get(HawkConfig.DOH_JSON, "");
            if (json.isEmpty()) json = dnsConfigJson;
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject dnsConfig = jsonArray.get(i).getAsJsonObject();
                dnsHttpsList.add(dnsConfig.has("name") ? dnsConfig.get("name").getAsString() : "Unknown");
            }
        } catch (Exception ignored) {}
    }

    static void initDnsOverHttps() {
        int dohSelector = 0;
        if (isHawkReady()) {
            dohSelector = Hawk.get(HawkConfig.DOH_URL, 0);
            setDnsList();
        }
        
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        try { setOkHttpSsl(builder); } catch (Throwable th) { th.printStackTrace(); }
        File cacheDir = App.getInstance().getCacheDir();
        if (cacheDir != null) builder.cache(new Cache(new File(cacheDir, "dohcache"), 10 * 1024 * 1024));
        OkHttpClient dohClient = builder.build();
        String dohUrl = "";
        if (dohSelector > 0) {
            try {
                String json = Hawk.get(HawkConfig.DOH_JSON, dnsConfigJson);
                JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
                if (dohSelector <= jsonArray.size()) dohUrl = jsonArray.get(dohSelector - 1).getAsJsonObject().get("url").getAsString();
            } catch (Exception ignored) {}
        }
        dnsOverHttps = new DnsOverHttps.Builder().client(dohClient).url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl)).build();
    }

    static void initExoOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        try { setOkHttpSsl(builder); } catch (Throwable th) { th.printStackTrace(); }
        builder.dns(dnsOverHttps != null ? dnsOverHttps : Dns.SYSTEM);
        ItvClient = builder.build();
        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(ItvClient);
    }

    static void initPicasso(OkHttpClient client) {
        try {
            Picasso picasso = new Picasso.Builder(App.getInstance()).downloader(new MyOkhttpDownLoader(client)).build();
            Picasso.setSingletonInstance(picasso);
        } catch (Throwable ignored) {}
    }

    private static synchronized void setOkHttpSsl(OkHttpClient.Builder builder) {
        try {
            final X509TrustManager trustAllCert = new X509TrustManager() {
                @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {}
                @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {}
                @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
            };
            builder.sslSocketFactory(new SSLSocketFactoryCompat(trustAllCert), trustAllCert);
            builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
