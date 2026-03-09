package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class DefaultConfig {

    // 预编译敏感词正则，提升高频匹配性能 (忽略大小写)
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "里番|成人|福利|伦理|激情|18\\+|限制级|三级|午夜|写真|av|sex|色情|诱惑|偷拍|自拍|制服|丝袜|无码|有码|欧美|岛国|乱伦|强奸|强暴|迷奸|迷情|性爱|性欲|🔞|erotic",
            Pattern.CASE_INSENSITIVE
    );

    public static boolean isSensitive(String name) {
        if (TextUtils.isEmpty(name)) return false;
        return SENSITIVE_PATTERN.matcher(name).find();
    }

    public static List<MovieSort.SortData> adjustSort(String sourceKey, List<MovieSort.SortData> list, boolean withMy) {
        List<MovieSort.SortData> data = new ArrayList<>();
        try {
            if (sourceKey != null) {
                SourceBean sb = ApiConfig.get().getSource(sourceKey);
                if (sb != null) {
                    ArrayList<String> categories = sb.getCategories();
                    if (categories != null && !categories.isEmpty()) {
                        for (String cate : categories) {
                            if (list != null) {
                                for (MovieSort.SortData sortData : list) {
                                    if (sortData != null && sortData.name != null && sortData.name.equals(cate)) {
                                        if (isSensitive(sortData.name)) continue;
                                        if (sortData.filters == null) sortData.filters = new ArrayList<>();
                                        data.add(sortData);
                                    }
                                }
                            }
                        }
                    } else if (list != null) {
                        for (MovieSort.SortData sortData : list) {
                            if (sortData != null) {
                                if (isSensitive(sortData.name)) continue;
                                if (sortData.filters == null) sortData.filters = new ArrayList<>();
                                data.add(sortData);
                            }
                        }
                    }
                } else if (list != null) {
                    for (MovieSort.SortData sortData : list) {
                        if (sortData != null) {
                            if (isSensitive(sortData.name)) continue;
                            if (sortData.filters == null) sortData.filters = new ArrayList<>();
                            data.add(sortData);
                        }
                    }
                }
            }
        } catch (Throwable th) { th.printStackTrace(); }
        
        if (withMy) {
            MovieSort.SortData my = new MovieSort.SortData("my0", "主页");
            my.sort = -200;
            data.add(0, my);
        }
        
        try { Collections.sort(data); } catch (Throwable ignored) {}
        return data;
    }

    public static int getAppVersionCode(Context mContext) {
        if (mContext == null) return -1;
        try { return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode; } catch (Throwable e) { return -1; }
    }

    public static String getAppVersionName(Context mContext) {
        if (mContext == null) return "";
        try { return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName; } catch (Throwable e) { return ""; }
    }

    public static String getFileSuffix(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int endP = name.lastIndexOf(".");
        return endP > -1 ? name.substring(endP) : "";
    }

    public static String getFilePrefixName(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        int start = fileName.lastIndexOf(".");
        return start > -1 ? fileName.substring(0, start) : fileName;
    }

    private static final Pattern snifferMatch = Pattern.compile(
            "http((?!http).){12,}?\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a)\\?.*|" +
            "http((?!http).){12,}\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a)|" +
            "http((?!http).)*?video/tos*|" +
            "http((?!http).){20,}?/m3u8\\?pt=m3u8.*|" +
            "http((?!http).)*?default\\.ixigua\\.com/.*|" +
            "http((?!http).)*?dycdn-tos\\.pstatp[^\\?]*|" +
            "http.*?/player/m3u8play\\.php\\?url=.*|" +
            "http.*?/player/.*?[pP]lay\\.php\\?url=.*|" +
            "http.*?/playlist/m3u8/\\?vid=.*|" +
            "http.*?\\.php\\?type=m3u8&.*|" +
            "http.*?/download.aspx\\?.*|" +
            "http.*?/api/up_api.php\\?.*|" +
            "https.*?\\.66yk\\.cn.*|" +
            "http((?!http).)*?netease\\.com/file/.*"
    );
    public static boolean isVideoFormat(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            if (TextUtils.isEmpty(path)) return false;
            return snifferMatch.matcher(url).find();
        } catch (Throwable ignored) {}
        return false;
    }

    public static String safeJsonString(JsonObject obj, String key, String defaultVal) {
        try {
            if (obj != null && obj.has(key)) return obj.get(key).isJsonObject() || obj.get(key).isJsonArray()?obj.get(key).toString().trim():obj.getAsJsonPrimitive(key).getAsString().trim();
            else return defaultVal;
        } catch (Throwable th) { return defaultVal; }
    }

    public static int safeJsonInt(JsonObject obj, String key, int defaultVal) {
        try {
            if (obj != null && obj.has(key)) return obj.getAsJsonPrimitive(key).getAsInt();
            else return defaultVal;
        } catch (Throwable th) { return defaultVal; }
    }

    public static ArrayList<String> safeJsonStringList(JsonObject obj, String key) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (obj != null && obj.has(key)) {
                if (obj.get(key).isJsonObject()) result.add(obj.get(key).getAsString());
                else for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsString());
            }
        } catch (Throwable th) {}
        return result;
    }

    public static String checkReplaceProxy(String urlOri) {
        if (urlOri != null && urlOri.startsWith("proxy://")) return urlOri.replace("proxy://", ControlManager.get().getAddress(true) + "proxy?");
        return urlOri;
    }

    private static final List<String> NO_AD_KEYWORDS = Arrays.asList("tx", "youku", "qq","qiyi", "letv", "leshi","sohu", "mgtv", "bilibili", "imgo","优酷", "芒果", "腾讯", "奇艺");
    public static boolean noAd(String flag) {
        if (flag == null || flag.isEmpty()) return false;
        for (String keyword : NO_AD_KEYWORDS) if (flag.equals(keyword) || flag.contains(keyword)) return true;
        return false;
    }
}
