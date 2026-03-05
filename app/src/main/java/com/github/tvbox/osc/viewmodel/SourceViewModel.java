package com.github.tvbox.osc.viewmodel;

import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsJson;
import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.UA;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.util.urlhttp.OkHttpUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.lzy.okgo.request.GetRequest;
import com.orhanobut.hawk.Hawk;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public class SourceViewModel extends ViewModel {
    public MutableLiveData<AbsSortXml> sortResult;
    public MutableLiveData<AbsXml> listResult;
    public MutableLiveData<AbsXml> searchResult;
    public MutableLiveData<AbsXml> quickSearchResult;
    public MutableLiveData<AbsXml> detailResult;
    public MutableLiveData<JSONObject> playResult;
    public Gson gson;

    public SourceViewModel() {
        sortResult = new MutableLiveData<>();
        listResult = new MutableLiveData<>();
        searchResult = new MutableLiveData<>();
        quickSearchResult = new MutableLiveData<>();
        detailResult = new MutableLiveData<>();
        playResult = new MutableLiveData<>();
        gson = new Gson();
    }

    public static final ExecutorService spThreadPool = Executors.newSingleThreadExecutor();

    private static final Map<String, AbsSortXml> sortCache = new LinkedHashMap<String, AbsSortXml>(5, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, AbsSortXml> eldest) {
            return size() > 5;
        }
    };

    public void getSort(final String sourceKey) {
        if (sourceKey == null) {
            sortResult.postValue(null);
            return;
        }
        AbsSortXml cached = sortCache.get(sourceKey);
        if (cached != null) {
            int homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
            if ((homeRec != 1) || (cached.videoList != null && !cached.videoList.isEmpty())) {
                sortResult.postValue(cached);
                return;
            }
        }
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        if (sourceBean == null || (sourceBean.getName().length() <= 3 && sourceBean.getName().endsWith("搜"))) {
            sortResult.postValue(null);
            return;
        }
        final int type = sourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(() -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> ApiConfig.get().getCSP(sourceBean).homeContent(true));
                try {
                    String sortJson = future.get(20, TimeUnit.SECONDS);
                    if (sortJson != null) {
                        final AbsSortXml sortXml = sortJson(sortResult, sortJson);
                        if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                            AbsXml absXml = json(null, sortJson, sourceBean.getKey());
                            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                                sortXml.videoList = absXml.movie.videoList;
                                sortResult.postValue(sortXml);
                                sortCache.put(sourceKey, sortXml);
                            } else {
                                getHomeRecList(sourceBean, null, videos -> {
                                    sortXml.videoList = videos;
                                    sortResult.postValue(sortXml);
                                    sortCache.put(sourceKey, sortXml);
                                });
                            }
                        } else if (sortXml != null) {
                            sortResult.postValue(sortXml);
                            sortCache.put(sourceKey, sortXml);
                        }
                    }
                } catch (Exception ignored) {
                } finally { executor.shutdown(); }
            });
        } else if (type == 0 || type == 1) {
            OkGo.<String>get(sourceBean.getApi()).tag(sourceBean.getKey() + "_sort").execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) {
                    AbsSortXml sortXml = (type == 0) ? sortXml(sortResult, response.body()) : sortJson(sortResult, response.body());
                    if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1 && sortXml.list != null && sortXml.list.videoList != null && !sortXml.list.videoList.isEmpty()) {
                        ArrayList<String> ids = new ArrayList<>();
                        for (Movie.Video vod : sortXml.list.videoList) ids.add(vod.id);
                        final AbsSortXml finalSortXml = sortXml;
                        getHomeRecList(sourceBean, ids, videos -> { finalSortXml.videoList = videos; sortResult.postValue(finalSortXml); sortCache.put(sourceKey, finalSortXml); });
                    } else if (sortXml != null) { sortResult.postValue(sortXml); sortCache.put(sourceKey, sortXml); }
                }
                @Override public void onError(Response<String> response) { sortResult.postValue(null); }
            });
        } else if (type == 4) {
            String extend = getFixUrl(sourceBean.getExt());
            if (URLEncoder.encode(extend).length() < 1000) {
                GetRequest<String> request = OkGo.<String>get(sourceBean.getApi()).tag(sourceBean.getKey() + "_sort").params("filter", "true");
                if (!TextUtils.isEmpty(extend)) request.params("extend", extend);
                request.execute(new AbsCallback<String>() {
                    @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                    @Override public void onSuccess(Response<String> response) {
                        final AbsSortXml sortXml = sortJson(sortResult, response.body());
                        if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                            AbsXml absXml = json(null, response.body(), sourceBean.getKey());
                            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && !absXml.movie.videoList.isEmpty()) {
                                sortXml.videoList = absXml.movie.videoList; sortResult.postValue(sortXml); sortCache.put(sourceKey, sortXml);
                            } else { getHomeRecList(sourceBean, null, videos -> { sortXml.videoList = videos; sortResult.postValue(sortXml); sortCache.put(sourceKey, sortXml); }); }
                        } else if (sortXml != null) { sortResult.postValue(sortXml); sortCache.put(sourceKey, sortXml); }
                    }
                    @Override public void onError(Response<String> response) { sortResult.postValue(null); }
                });
            }
        }
    }

    public void getList(MovieSort.SortData sortData, int page) {
        if (sortData.id.equals("douban_recommend")) {
            getDoubanRecommend(page);
            return;
        }
        SourceBean homeSourceBean = ApiConfig.get().getHomeSourceBean();
        if (homeSourceBean == null) return;
        int type = homeSourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(() -> {
                try {
                    Spider sp = ApiConfig.get().getCSP(homeSourceBean);
                    String json = sp.categoryContent(sortData.id, page + "", true, sortData.filterSelect);
                    json(listResult, json, homeSourceBean.getKey());
                } catch (Throwable th) { th.printStackTrace(); }
            });
        } else if (type == 0 || type == 1) {
            OkGo.<String>get(homeSourceBean.getApi()).tag(homeSourceBean.getApi()).params("ac", type == 0 ? "videolist" : "detail").params("t", sortData.id).params("pg", page).params(sortData.filterSelect).params("f", (sortData.filterSelect == null || sortData.filterSelect.size() <= 0) ? "" : new JSONObject(sortData.filterSelect).toString()).execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { if (type == 0) xml(listResult, response.body(), homeSourceBean.getKey()); else json(listResult, response.body(), homeSourceBean.getKey()); }
                @Override public void onError(Response<String> response) { listResult.postValue(null); }
            });
        } else if (type == 4) {
            String extend = getFixUrl(homeSourceBean.getExt());
            String ext = "";
            if (sortData.filterSelect != null && sortData.filterSelect.size() > 0) {
                try { ext = Base64.encodeToString(new JSONObject(sortData.filterSelect).toString().getBytes("UTF-8"), Base64.DEFAULT | Base64.NO_WRAP); } catch (Exception ignored) {}
            } else ext = Base64.encodeToString("{}".getBytes(), Base64.DEFAULT | Base64.NO_WRAP);
            GetRequest<String> request = OkGo.<String>get(homeSourceBean.getApi()).tag(homeSourceBean.getApi()).params("ac", "detail").params("filter", "true").params("t", sortData.id).params("pg", page).params("ext", ext);
            if (!TextUtils.isEmpty(extend)) request.params("extend", extend);
            request.execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { json(listResult, response.body(), homeSourceBean.getKey()); }
                @Override public void onError(Response<String> response) { listResult.postValue(null); }
            });
        }
    }

    public void getDoubanRecommend(int page) {
        int start = (page - 1) * 20;
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=" + start + "&year_range=" + year + "," + year;
        OkGo.<String>get(doubanUrl)
                .headers("User-Agent", UA.randomOne())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            AbsXml absXml = new AbsXml();
                            absXml.movie = new Movie();
                            absXml.movie.videoList = new ArrayList<>();
                            
                            JsonObject infoJson = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray array = infoJson.getAsJsonArray("data");
                            for (JsonElement ele : array) {
                                JsonObject obj = ele.getAsJsonObject();
                                Movie.Video vod = new Movie.Video();
                                vod.name = obj.get("title").getAsString();
                                vod.note = obj.get("rate").getAsString();
                                if (!vod.note.isEmpty()) vod.note += " 分";
                                vod.pic = obj.get("cover").getAsString()
                                        + "@User-Agent=" + UA.randomOne()
                                        + "@Referer=https://www.douban.com/";
                                vod.sourceKey = ""; // trigger search
                                absXml.movie.videoList.add(vod);
                            }
                            absXml.movie.page = page;
                            absXml.movie.pagecount = 10;
                            listResult.postValue(absXml);
                        } catch (Exception e) {
                            listResult.postValue(null);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        listResult.postValue(null);
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() != null ? response.body().string() : "";
                    }
                });
    }

    interface HomeRecCallback { void done(List<Movie.Video> videos); }

    void getHomeRecList(SourceBean sourceBean, ArrayList<String> ids, HomeRecCallback callback) {
        int type = sourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(() -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> ApiConfig.get().getCSP(sourceBean).homeVideoContent());
                try {
                    String sortJson = future.get(10, TimeUnit.SECONDS);
                    if (sortJson != null) { AbsXml absXml = json(null, sortJson, sourceBean.getKey()); callback.done(absXml != null && absXml.movie != null ? absXml.movie.videoList : null); }
                    else callback.done(null);
                } catch (Exception ignored) { callback.done(null); } finally { executor.shutdown(); }
            });
        } else if (type == 0 || type == 1) {
            OkGo.<String>get(sourceBean.getApi()).tag("detail").params("ac", type == 0 ? "videolist" : "detail").params("ids", TextUtils.join(",", ids)).execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { AbsXml absXml = (type == 0) ? xml(null, response.body(), sourceBean.getKey()) : json(null, response.body(), sourceBean.getKey()); callback.done(absXml != null && absXml.movie != null ? absXml.movie.videoList : null); }
                @Override public void onError(Response<String> response) { callback.done(null); }
            });
        }
    }

    public void getDetail(String sourceKey, String urlid) {
        if (urlid.startsWith("push://") && ApiConfig.get().getSource("push_agent") != null) {
            String pushUrl = urlid.substring(7);
            try { if (pushUrl.startsWith("b64:")) pushUrl = new String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8"); else pushUrl = URLDecoder.decode(pushUrl); } catch (Exception ignored) {}
            sourceKey = "push_agent"; urlid = pushUrl;
        }
        final String id = urlid; SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        if (sourceBean == null) return;
        int type = sourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(() -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> ApiConfig.get().getCSP(sourceBean).detailContent(java.util.Collections.singletonList(id)));
                try { json(detailResult, future.get(15, TimeUnit.SECONDS), sourceBean.getKey()); } catch (Exception ignored) { json(detailResult, null, sourceBean.getKey()); } finally { executor.shutdown(); }
            });
        } else if (type == 0 || type == 1 || type == 4) {
            String extend = getFixUrl(sourceBean.getExt());
            GetRequest<String> request = OkGo.<String>get(sourceBean.getApi()).tag("detail").params("ac", type == 0 ? "videolist" : "detail").params("ids", id);
            if (!TextUtils.isEmpty(extend)) request.params("extend", extend);
            request.execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { if (type == 0) xml(detailResult, response.body(), sourceBean.getKey()); else json(detailResult, response.body(), sourceBean.getKey()); }
                @Override public void onError(Response<String> response) { json(detailResult, "", sourceBean.getKey()); }
            });
        }
    }

    public void getSearch(String sourceKey, String wd) {
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        if (sourceBean == null) return;
        int type = sourceBean.getType();
        if (type == 3) {
            try { json(searchResult, ApiConfig.get().getCSP(sourceBean).searchContent(wd, false), sourceBean.getKey()); } catch (Throwable th) { json(searchResult, "", sourceBean.getKey()); }
        } else if (type == 0 || type == 1) {
            OkGo.<String>get(sourceBean.getApi()).params("wd", wd).params(type == 1 ? "ac" : null, type == 1 ? "detail" : null).tag("search").execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { if (type == 0) xml(searchResult, response.body(), sourceBean.getKey()); else json(searchResult, response.body(), sourceBean.getKey()); }
                @Override public void onError(Response<String> response) { EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null)); }
            });
        } else if (type == 4) {
            String extend = getFixUrl(sourceBean.getExt()); String encodedWd = wd;
            try { encodedWd = URLEncoder.encode(wd, "UTF-8"); } catch (Exception ignored) {}
            GetRequest<String> request = OkGo.<String>get(sourceBean.getApi()).tag("search").params("wd", encodedWd).params("ac", "detail").params("quick", "false");
            if (!TextUtils.isEmpty(extend)) request.params("extend", extend);
            request.execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { json(searchResult, response.body(), sourceBean.getKey()); }
                @Override public void onError(Response<String> response) { EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null)); }
            });
        }
    }

    public void getQuickSearch(String sourceKey, String wd) {
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        if (sourceBean == null) return;
        int type = sourceBean.getType();
        if (type == 3) { try { json(quickSearchResult, ApiConfig.get().getCSP(sourceBean).searchContent(wd, true), sourceBean.getKey()); } catch (Throwable ignored) {} }
        else if (type == 0 || type == 1) {
            OkGo.<String>get(sourceBean.getApi()).params("wd", wd).params(type == 1 ? "ac" : null, type == 1 ? "detail" : null).tag("quick_search").execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { if (type == 0) xml(quickSearchResult, response.body(), sourceBean.getKey()); else json(quickSearchResult, response.body(), sourceBean.getKey()); }
                @Override public void onError(Response<String> response) { EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null)); }
            });
        } else if (type == 4) {
            String extend = getFixUrl(sourceBean.getExt());
            GetRequest<String> request = OkGo.<String>get(sourceBean.getApi()).tag("search").params("wd", wd).params("ac", "detail").params("quick", "true");
            if (!TextUtils.isEmpty(extend)) request.params("extend", extend);
            request.execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) { json(quickSearchResult, response.body(), sourceBean.getKey()); }
                @Override public void onError(Response<String> response) { EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null)); }
            });
        }
    }

    public void getPlay(String sourceKey, String playFlag, String progressKey, String url, String subtitleKey) {
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        if (sourceBean == null) return;
        int type = sourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(() -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> ApiConfig.get().getCSP(sourceBean).playerContent(playFlag, url, ApiConfig.get().getVipParseFlags()));
                try {
                    String json = future.get(10, TimeUnit.SECONDS);
                    if (!TextUtils.isEmpty(json)) {
                        JSONObject result = new JSONObject(json); result.put("key", url); result.put("proKey", progressKey); result.put("subtKey", subtitleKey);
                        if (!result.has("flag")) result.put("flag", playFlag); playResult.postValue(result);
                    } else playResult.postValue(null);
                } catch (Exception ignored) { playResult.postValue(null); } finally { executor.shutdown(); }
            });
        } else if (type == 0 || type == 1) {
            JSONObject result = new JSONObject();
            try { result.put("key", url); String playUrl = sourceBean.getPlayerUrl().trim(); result.put("parse", (DefaultConfig.isVideoFormat(url) && playUrl.isEmpty()) ? 0 : 1); result.put("url", url); result.put("proKey", progressKey); result.put("subtKey", subtitleKey); result.put("playUrl", playUrl); result.put("flag", playFlag); playResult.postValue(result); } catch (Throwable ignored) { playResult.postValue(null); }
        } else if (type == 4) {
            String extend = getFixUrl(sourceBean.getExt()); GetRequest<String> request = OkGo.<String>get(sourceBean.getApi()).tag("play").params("play", url).params("flag", playFlag);
            if (!TextUtils.isEmpty(extend)) request.params("extend", extend);
            request.execute(new AbsCallback<String>() {
                @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                @Override public void onSuccess(Response<String> response) {
                    try { JSONObject result = new JSONObject(response.body()); result.put("key", url); result.put("proKey", progressKey); result.put("subtKey", subtitleKey); if (!result.has("flag")) result.put("flag", playFlag); playResult.postValue(result); } catch (Throwable ignored) { playResult.postValue(null); }
                }
                @Override public void onError(Response<String> response) { playResult.postValue(null); }
            });
        }
    }

    private static final ConcurrentHashMap<String, String> extendCache = new ConcurrentHashMap<>();

    private String getFixUrl(final String extend) {
        if (TextUtils.isEmpty(extend)) return "";
        if (!extend.startsWith("http")) return extend;
        final String key = MD5.string2MD5(extend);
        if (extendCache.containsKey(key)) return extendCache.get(key);
        Future<String> future = spThreadPool.submit(() -> {
            OkHttpClient client = OkGoHelper.getDefaultClient();
            if (client == null) return extend;
            String result = OkHttpUtil.string(client, extend, null, null, null, null);
            if (!TextUtils.isEmpty(result)) {
                result = tryMinifyJson(result);
                if (result.length() < 2500) { extendCache.putIfAbsent(key, result); return result; }
            }
            return extend;
        });
        try { return future.get(1, TimeUnit.SECONDS); } catch (Exception e) { return extend; }
    }

    private String tryMinifyJson(String raw) { try { return gson.toJson(JsonParser.parseString(raw.trim())); } catch (Exception e) { return raw; } }

    private MovieSort.SortFilter getSortFilter(JsonObject obj) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(); JsonArray kv = obj.getAsJsonArray("value");
        for (JsonElement ele : kv) { JsonObject ele_obj = ele.getAsJsonObject(); values.put(ele_obj.has("n") ? ele_obj.get("n").getAsString() : "", ele_obj.has("v") ? ele_obj.get("v").getAsString() : ""); }
        MovieSort.SortFilter filter = new MovieSort.SortFilter(); filter.key = obj.get("key").getAsString(); filter.name = obj.get("name").getAsString(); filter.values = values; return filter;
    }

    private AbsSortXml sortJson(MutableLiveData<AbsSortXml> result, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject(); AbsSortXml data = ((AbsSortJson)gson.fromJson(obj, new TypeToken<AbsSortJson>() {}.getType())).toAbsSortXml();
            if (obj.has("filters")) {
                LinkedHashMap<String, ArrayList<MovieSort.SortFilter>> sortFilters = new LinkedHashMap<>(); JsonObject filters = obj.getAsJsonObject("filters");
                for (String key : filters.keySet()) { ArrayList<MovieSort.SortFilter> sortFilter = new ArrayList<>(); JsonElement one = filters.get(key); if (one.isJsonObject()) sortFilter.add(getSortFilter(one.getAsJsonObject())); else for (JsonElement ele : one.getAsJsonArray()) sortFilter.add(getSortFilter(ele.getAsJsonObject())); sortFilters.put(key, sortFilter); }
                for (MovieSort.SortData sort : data.classes.sortList) if (sortFilters.containsKey(sort.id)) sort.filters = sortFilters.get(sort.id);
            }
            return data;
        } catch (Exception e) { return null; }
    }

    private AbsSortXml sortXml(MutableLiveData<AbsSortXml> result, String xml) {
        try {
            XStream xstream = new XStream(new DomDriver()); xstream.autodetectAnnotations(true); xstream.processAnnotations(AbsSortXml.class); xstream.ignoreUnknownElements();
            AbsSortXml data = (AbsSortXml) xstream.fromXML(xml.replace("<year></year>", "<year>0</year>").replace("<state></state>", "<state>0</state>"));
            for (MovieSort.SortData sort : data.classes.sortList) if (sort.filters == null) sort.filters = new ArrayList<>(); return data;
        } catch (Exception e) { return null; }
    }

    private void absXml(AbsXml data, String sourceKey) {
        if (data != null && data.movie != null && data.movie.videoList != null) {
            for (Movie.Video video : data.movie.videoList) {
                if (video.urlBean != null && video.urlBean.infoList != null) {
                    for (Movie.Video.UrlBean.UrlInfo urlInfo : video.urlBean.infoList) {
                        String[] str = urlInfo.urls.contains("#") ? urlInfo.urls.split("#") : new String[]{urlInfo.urls}; List<Movie.Video.UrlBean.UrlInfo.InfoBean> infoBeanList = new ArrayList<>();
                        for (String s : str) { String[] ss = s.split("\\$"); if (ss.length >= 2) infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1])); else if (ss.length > 0) infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean((infoBeanList.size() + 1) + "", ss[0])); }
                        urlInfo.beanList = infoBeanList;
                    }
                }
                video.sourceKey = sourceKey;
            }
        }
    }

    private AbsXml checkPush(AbsXml data) {
        if (data != null && data.movie != null && data.movie.videoList != null && !data.movie.videoList.isEmpty()) {
            Movie.Video video = data.movie.videoList.get(0);
            if (video != null && video.urlBean != null && video.urlBean.infoList != null) {
                for (int i = 0; i < video.urlBean.infoList.size(); i++) {
                    Movie.Video.UrlBean.UrlInfo urlinfo = video.urlBean.infoList.get(i);
                    if (urlinfo != null && urlinfo.beanList != null) {
                        for (Movie.Video.UrlBean.UrlInfo.InfoBean infoBean : urlinfo.beanList) {
                            if (infoBean.url.startsWith("push://")) {
                                String pushUrl = infoBean.url.substring(7);
                                try { if (pushUrl.startsWith("b64:")) pushUrl = new String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8"); else pushUrl = URLDecoder.decode(pushUrl); } catch (Exception ignored) {}
                                final AbsXml[] resData = {null}; final CountDownLatch latch = new CountDownLatch(1); String finalPushUrl = pushUrl;
                                spThreadPool.execute(() -> {
                                    SourceBean sb = ApiConfig.get().getSource("push_agent"); if (sb == null) { latch.countDown(); return; }
                                    if (sb.getType() == 4) { OkGo.<String>get(sb.getApi()).tag("detail").params("ac", "detail").params("ids", finalPushUrl).execute(new AbsCallback<String>() {
                                            @Override public String convertResponse(okhttp3.Response response) throws Throwable { return response.body() != null ? response.body().string() : ""; }
                                            @Override public void onSuccess(Response<String> response) { if (!TextUtils.isEmpty(response.body())) { try { resData[0] = ((AbsJson)gson.fromJson(response.body(), new TypeToken<AbsJson>() {}.getType())).toAbsXml(); absXml(resData[0], sb.getKey()); } catch (Exception ignored) {} } latch.countDown(); }
                                            @Override public void onError(Response<String> response) { latch.countDown(); }
                                        });
                                    } else { try { String res = ApiConfig.get().getCSP(sb).detailContent(java.util.Collections.singletonList(finalPushUrl)); if (!TextUtils.isEmpty(res)) { resData[0] = ((AbsJson)gson.fromJson(res, new TypeToken<AbsJson>() {}.getType())).toAbsXml(); absXml(resData[0], sb.getKey()); } } catch (Throwable ignored) {} latch.countDown(); }
                                });
                                try { latch.await(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
                                if (resData[0] != null && resData[0].movie != null && !resData[0].movie.videoList.isEmpty()) {
                                    Movie.Video resVideo = resData[0].movie.videoList.get(0);
                                    if (resVideo.urlBean != null && !resVideo.urlBean.infoList.isEmpty()) {
                                        if (urlinfo.beanList.size() == 1) video.urlBean.infoList.remove(i); else urlinfo.beanList.remove(infoBean);
                                        for (Movie.Video.UrlBean.UrlInfo resUrlinfo : resVideo.urlBean.infoList) if (resUrlinfo != null && resUrlinfo.beanList != null) video.urlBean.infoList.add(resUrlinfo);
                                        video.sourceKey = "push_agent"; return data;
                                    }
                                }
                                infoBean.name = "解析失败 >>> " + infoBean.name;
                            }
                        }
                    }
                }
            }
        }
        return data;
    }

    public void checkThunder(AbsXml data, int index) {
        if (data != null && data.movie != null && data.movie.videoList != null && data.movie.videoList.size() == 1) {
            Movie.Video video = data.movie.videoList.get(0);
            if (video != null && video.urlBean != null && video.urlBean.infoList != null) {
                boolean hasThunder = false;
                thunderLoop: for (Movie.Video.UrlBean.UrlInfo urlInfo : video.urlBean.infoList) for (Movie.Video.UrlBean.UrlInfo.InfoBean info : urlInfo.beanList) if (Thunder.isSupportUrl(info.url)) { hasThunder = true; break thunderLoop; }
                if (hasThunder) {
                    Thunder.parse(App.getInstance(), video.urlBean, new Thunder.ThunderCallback() {
                        @Override public void status(int code, String info) { if (code < 0) { video.urlBean.infoList.get(0).beanList.get(0).name = info; detailResult.postValue(data); } }
                        @Override public void list(Map<Integer, String> urlMap) {
                            for (int key : urlMap.keySet()) {
                                String playList = urlMap.get(key); video.urlBean.infoList.get(key).urls = playList;
                                String[] str = playList.split("#"); List<Movie.Video.UrlBean.UrlInfo.InfoBean> infoBeanList = new ArrayList<>();
                                for (String s : str) { String[] ss = s.split("\\$"); if (ss.length >= 2) infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1])); else if (ss.length > 0) infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean((infoBeanList.size() + 1) + "", ss[0])); }
                                video.urlBean.infoList.get(key).beanList = infoBeanList;
                            }
                            detailResult.postValue(data);
                        }
                        @Override public void play(String url) {}
                    });
                    return;
                }
            }
        }
        if (index == 0) detailResult.postValue(data);
    }

    private AbsXml xml(MutableLiveData<AbsXml> result, String xml, String sourceKey) {
        try {
            XStream xstream = new XStream(new DomDriver()); xstream.autodetectAnnotations(true); xstream.processAnnotations(AbsSortXml.class); xstream.ignoreUnknownElements();
            AbsXml data = (AbsXml) xstream.fromXML(xml.replace("<year></year>", "<year>0</year>").replace("<state></state>", "<state>0</state>"));
            absXml(data, sourceKey);
            if (searchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data));
            else if (quickSearchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data));
            else if (result != null) { if (result == detailResult) { data = checkPush(data); checkThunder(data, 0); } else result.postValue(data); }
            return data;
        } catch (Exception e) {
            if (searchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
            else if (quickSearchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
            else if (result != null) result.postValue(null);
            return null;
        }
    }

    private AbsXml json(MutableLiveData<AbsXml> result, String json, String sourceKey) {
        try {
            AbsXml data = ((AbsJson)gson.fromJson(json, new TypeToken<AbsJson>() {}.getType())).toAbsXml();
            absXml(data, sourceKey);
            if (searchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data));
            else if (quickSearchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data));
            else if (result != null) { if (result == detailResult) { data = checkPush(data); checkThunder(data, 0); } else result.postValue(data); }
            return data;
        } catch (Exception e) {
            if (searchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
            else if (quickSearchResult == result) EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
            else if (result != null) result.postValue(null);
            return null;
        }
    }

    @Override protected void onCleared() { super.onCleared(); }
}
