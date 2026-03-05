package com.github.tvbox.osc.util.urlhttp;

import android.text.TextUtils;
import com.github.tvbox.osc.util.OkGoHelper;
import java.io.IOException;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

class OKRequest {
    private final String mMethodType;
    private String mUrl;
    private Object mTag = null;
    private final Map<String, String> mParamsMap;
    private final String mJsonStr;
    private final Map<String, String> mHeaderMap;
    private final OKCallBack mCallBack;
    private okhttp3.Request mOkHttpRequest;
    private okhttp3.Request.Builder mRequestBuilder;

    OKRequest(String methodType, String url, Map<String, String> paramsMap, Map<String, String> headerMap, OKCallBack callBack) {
        this(methodType, url, null, paramsMap, headerMap, callBack);
    }

    OKRequest(String methodType, String url, String jsonStr, Map<String, String> headerMap, OKCallBack callBack) {
        this(methodType, url, jsonStr, null, headerMap, callBack);
    }

    private OKRequest(String methodType, String url, String jsonStr, Map<String, String> paramsMap, Map<String, String> headerMap, OKCallBack callBack) {
        this.mMethodType = methodType;
        this.mUrl = url;
        this.mJsonStr = jsonStr;
        this.mParamsMap = paramsMap;
        this.mHeaderMap = headerMap;
        this.mCallBack = callBack;
        this.mOkHttpRequest = null;
        buildRequest();
    }

    public void setTag(Object tag) {
        this.mTag = tag;
        buildRequest();
    }

    private void buildRequest() {
        try {
            if (TextUtils.isEmpty(mUrl)) return;
            mRequestBuilder = new okhttp3.Request.Builder();
            if (OkHttpUtil.METHOD_GET.equals(mMethodType)) {
                setGetParams();
            } else {
                mRequestBuilder.post(getRequestBody());
            }
            mRequestBuilder.url(mUrl);
            if (mTag != null) mRequestBuilder.tag(mTag);
            if (mHeaderMap != null) setHeader();
            mOkHttpRequest = mRequestBuilder.build();
        } catch (Throwable ignored) {}
    }

    private RequestBody getRequestBody() {
        if (!TextUtils.isEmpty(mJsonStr)) {
            return RequestBody.create(MediaType.parse("application/json; charset=utf-8"), mJsonStr);
        }
        FormBody.Builder formBody = new FormBody.Builder();
        if (mParamsMap != null) {
            for (String key : mParamsMap.keySet()) {
                String val = mParamsMap.get(key);
                if (val != null) formBody.add(key, val);
            }
        }
        return formBody.build();
    }

    private void setGetParams() {
        if (mParamsMap != null && !mParamsMap.isEmpty()) {
            StringBuilder sb = new StringBuilder(mUrl);
            sb.append(mUrl.contains("?") ? "&" : "?");
            for (String key : mParamsMap.keySet()) {
                sb.append(key).append("=").append(mParamsMap.get(key)).append("&");
            }
            mUrl = sb.deleteCharAt(sb.length() - 1).toString();
        }
    }

    private void setHeader() {
        if (mHeaderMap != null) {
            for (String key : mHeaderMap.keySet()) {
                String val = mHeaderMap.get(key);
                if (val != null) mRequestBuilder.addHeader(key, val);
            }
        }
    }

    void execute(OkHttpClient client) {
        if (client == null) client = OkGoHelper.getDefaultClient();
        if (client == null || mOkHttpRequest == null) {
            if (mCallBack != null) mCallBack.onFailure(null, new IOException("Client or Request is null"));
            return;
        }
        try {
            Response response = client.newCall(mOkHttpRequest).execute();
            if (mCallBack != null) mCallBack.onSuccess(null, response);
        } catch (Throwable e) {
            if (mCallBack != null) mCallBack.onError(null, new Exception(e));
        }
    }

    void call(OkHttpClient client) {
        if (client == null) client = OkGoHelper.getDefaultClient();
        if (client == null || mOkHttpRequest == null) {
            if (mCallBack != null) mCallBack.onFailure(null, new IOException("Client or Request is null"));
            return;
        }
        client.newCall(mOkHttpRequest).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { if (mCallBack != null) mCallBack.onError(call, e); }
            @Override public void onResponse(Call call, Response response) { if (mCallBack != null) mCallBack.onSuccess(call, response); }
        });
    }
}
