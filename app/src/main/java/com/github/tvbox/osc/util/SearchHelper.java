package com.github.tvbox.osc.util;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

public class SearchHelper {

    public static HashMap<String, String> getSourcesForSearch() {
        HashMap<String, String> mCheckSources;
        try {
            String api = Hawk.get(HawkConfig.API_URL, "");
            if(api.isEmpty()) return null;
            HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, new HashMap<>());
            mCheckSources = mCheckSourcesForApi.get(api);
        } catch (Exception e) {
            return null;
        }
        
        // 核心修复：如果缓存的搜索源为空，或者配置更新了站点，重新获取
        HashMap<String, String> currentSources = getSources();
        if (mCheckSources == null || mCheckSources.isEmpty()) {
            mCheckSources = currentSources;
        } else {
            // 自动同步：确保新增的站点被选中
            boolean changed = false;
            for (String key : currentSources.keySet()) {
                if (!mCheckSources.containsKey(key)) {
                    mCheckSources.put(key, "1");
                    changed = true;
                }
            }
            // 移除已经不存在的站点
            Iterator<Map.Entry<String, String>> it = mCheckSources.entrySet().iterator();
            while (it.hasNext()) {
                if (!currentSources.containsKey(it.next().getKey())) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                putCheckedSources(mCheckSources, false);
            }
        }
        return mCheckSources;
    }

    public static void putCheckedSources(HashMap<String, String> mCheckSources, boolean isAll) {
        String api = Hawk.get(HawkConfig.API_URL, "");
        if (api.isEmpty()) {
            return;
        }
        HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, new HashMap<>());

        if(isAll){
            mCheckSourcesForApi.remove(api);
        }else {
            mCheckSourcesForApi.put(api, mCheckSources);
        }
        SearchActivity.setCheckedSourcesForSearch(mCheckSources);
        Hawk.put(HawkConfig.SOURCES_FOR_SEARCH, mCheckSourcesForApi);
    }

    public static HashMap<String, String> getSources(){
        HashMap<String, String> mCheckSources = new HashMap<>();
        // 获取所有可搜索的源
        List<SourceBean> searchList = ApiConfig.get().getSearchSourceBeanList();
        for (SourceBean bean : searchList) {
            mCheckSources.put(bean.getKey(), "1");
        }
        return mCheckSources;
    }

    public static List<String> splitWords(String text) {
        List<String> result = new ArrayList<>();
        result.add(text);
        if (text != null) {
            String[] parts = text.split("\\W+");
            if (parts.length > 1) {
                for (String p : parts) {
                    if (p.length() > 1) result.add(p);
                }
            }
        }
        return result;
    }

}
