package com.github.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.UA;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
public class UserFragment extends BaseLazyFragment implements View.OnClickListener {
    private LinearLayout tvLive;
    private LinearLayout tvSearch;
    private LinearLayout tvSetting;
    private LinearLayout tvHistory;
    private LinearLayout tvCollect;
    private LinearLayout tvPush;
    private LinearLayout tvDouban; 
    private LinearLayout tvRecentHot; // 新增近期热播按钮
    public static HomeHotVodAdapter homeHotVodAdapter;
    private List<Movie.Video> homeSourceRec;
    public static TvRecyclerView tvHotList;
    private int topStart = 0;
    private final int topLimit = 50;

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) {
            tvHotList.setVisibility(View.VISIBLE);
            tvHotList.setHasFixedSize(true);
            int spanCount = 5;
            if(style!=null && Hawk.get(HawkConfig.HOME_REC, 0) == 1)spanCount=ImgUtil.spanCountByStyle(style,spanCount);
            tvHotList.setLayoutManager(new V7GridLayoutManager(this.mContext, spanCount));
            int paddingLeft = getResources().getDimensionPixelSize(R.dimen.vs_15);
            int paddingTop = getResources().getDimensionPixelSize(R.dimen.vs_10);
            int paddingRight = getResources().getDimensionPixelSize(R.dimen.vs_15);
            int paddingBottom = getResources().getDimensionPixelSize(R.dimen.vs_10);
            tvHotList.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        } else {
            tvHotList.setVisibility(View.VISIBLE);
            tvHotList.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.HORIZONTAL, false));
            int paddingLeft = getResources().getDimensionPixelSize(R.dimen.vs_15);
            int paddingTop = getResources().getDimensionPixelSize(R.dimen.vs_40);
            int paddingRight = getResources().getDimensionPixelSize(R.dimen.vs_15);
            int paddingBottom = getResources().getDimensionPixelSize(R.dimen.vs_40);
            tvHotList.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(20);
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vod.note = "上次看到" + vodInfo.playNote;
                vodList.add(vod);
            }
            homeHotVodAdapter.setNewData(vodList);
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    private void jumpSearch(Movie.Video vod){
        Intent newIntent;
        if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
            newIntent = new Intent(mContext, FastSearchActivity.class);
        }else {
            newIntent = new Intent(mContext, SearchActivity.class);
        }
        newIntent.putExtra("title", vod.name);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mActivity.startActivity(newIntent);
    }
    private ImgUtil.Style style;
    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.tvPush);
        tvDouban = findViewById(R.id.tvDouban); 
        tvRecentHot = findViewById(R.id.tvRecentHot); 
        
        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);
        tvDouban.setOnClickListener(this); 
        tvRecentHot.setOnClickListener(this); 
        
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);
        tvDouban.setOnFocusChangeListener(focusChangeListener); 
        tvRecentHot.setOnFocusChangeListener(focusChangeListener); 
        
        tvHotList = findViewById(R.id.tvHotList);
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && homeSourceRec!=null) {
            style=ImgUtil.initStyle();
        }
        String tvRate="";
        if(Hawk.get(HawkConfig.HOME_REC, 0) == 0){
            tvRate="热播";
        }else if(Hawk.get(HawkConfig.HOME_REC, 0) == 1){
          tvRate= homeSourceRec!=null?"站点推荐":"热播";
        }else if(Hawk.get(HawkConfig.HOME_REC, 0) == 3){ 
            tvRate="TOP";
        }
        
        homeHotVodAdapter = new HomeHotVodAdapter(style,tvRate);
        homeHotVodAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2) && HawkConfig.hotVodDelete) {
                    homeHotVodAdapter.remove(position);
                    VodInfo vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
                    assert vodInfo != null;
                    RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo);
                    Toast.makeText(mContext, "已删除当前记录", Toast.LENGTH_SHORT).show();
               } else if (vod.id != null && !vod.id.isEmpty()) {
                    if (ApiConfig.get().getSourceBeanList().isEmpty()) {
                        Toast.makeText(mContext, "请在【设置】界面配置视频源地址", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    SourceBean sourceBean = ApiConfig.get().getSource(vod.sourceKey);
                    if(sourceBean!=null){
                        bundle.putString("picture", vod.pic);
                        jumpActivity(DetailActivity.class, bundle);
                    }else {
                        jumpSearch(vod);
                    }
                } else {
                    if (ApiConfig.get().getSourceBeanList().isEmpty()) {
                        Toast.makeText(mContext, "请在【设置】界面配置视频源地址", Toast.LENGTH_LONG).show();
                        return;
                    }
                    jumpSearch(vod);
                }
            }
        });
        
        homeHotVodAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                assert vod != null;
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2)) {
                    HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
                    homeHotVodAdapter.notifyDataSetChanged();
                } else {
                    if (ApiConfig.get().getSourceBeanList().isEmpty()) {
                        Toast.makeText(mContext, "请在【设置】界面配置视频源地址", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);                    
                }
                return true;
            }    
        });

        tvHotList.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        homeHotVodAdapter.setOnLoadMoreListener(() -> {
            int homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
            if (homeRec == 3) {
                setDouBanTop250Data(homeHotVodAdapter, false);
            } else {
                homeHotVodAdapter.loadMoreEnd();
            }
        }, tvHotList);
        tvHotList.setAdapter(homeHotVodAdapter);

        initHomeHotVod(homeHotVodAdapter);
    }

    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        if (homeRec == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                return;
            }
        } else if (homeRec == 2) {
            return;
        } else if (homeRec == 3) {
            setDouBanTop250Data(adapter, true);
            return;
        }
        setDouBanData(adapter);
    }

    private void setDouBanData(HomeHotVodAdapter adapter) {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        adapter.setNewData(hotMovies);
                        adapter.loadMoreEnd();
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            if (isAdded()) {
                                mActivity.runOnUiThread(() -> {
                                    adapter.setNewData(loadHots(netJson));
                                    adapter.loadMoreEnd();
                                });
                            }
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void setDouBanTop250Data(HomeHotVodAdapter adapter, boolean refresh) {
        try {
            if (refresh) {
                topStart = 0;
                adapter.setEnableLoadMore(true);
            }
            String doubanTopUrl = "https://movie.douban.com/j/chart/top_list?type=11&interval_id=100:90&action=&start=" + topStart + "&limit=" + topLimit;
            OkGo.<String>get(doubanTopUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            if (isAdded()) {
                                mActivity.runOnUiThread(() -> {
                                    ArrayList<Movie.Video> newItems = loadTop250(netJson, topStart);
                                    if (refresh) {
                                        adapter.setNewData(newItems);
                                    } else {
                                        adapter.addData(newItems);
                                    }
                                    if (newItems.size() < topLimit || adapter.getData().size() >= 500) {
                                        adapter.loadMoreEnd();
                                    } else {
                                        adapter.loadMoreComplete();
                                        topStart += topLimit;
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            if (isAdded()) {
                                mActivity.runOnUiThread(() -> adapter.loadMoreFail());
                            }
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private ArrayList<Movie.Video> loadTop250(String json, int offset) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonArray array = new Gson().fromJson(json, JsonArray.class);
            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.get(i).getAsJsonObject();
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("score").getAsString() + " 分";
                vod.pic = obj.get("cover_url").getAsString()
                        + "@User-Agent=" + UA.randomOne()
                        + "@Referer=https://www.douban.com/";
                vod.tag = String.valueOf(offset + i + 1); 
                result.add(vod);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            int limit = Math.min(array.size(), 25);
            for (int i = 0; i < limit; i++) {
                JsonElement ele = array.get(i);
                JsonObject obj = ele.getAsJsonObject();
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                if (!vod.note.isEmpty()) vod.note += " 分";
                vod.pic = obj.get("cover").getAsString()
                        + "@User-Agent=" + UA.randomOne()
                        + "@Referer=https://www.douban.com/";

                result.add(vod);
            }
        } catch (Throwable th) {

        }
        return result;
    }

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus)
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            else
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
        }
    };

    @Override
    public void onClick(View v) {
        HawkConfig.hotVodDelete = false;
        FastClickCheckUtil.check(v);
        if (v.getId() == R.id.tvLive) {
            if(Hawk.get(HawkConfig.LIVE_GROUP_LIST,new JsonArray()).isEmpty()){
                Toast.makeText(mContext, "直播源为空", Toast.LENGTH_SHORT).show();
            }else {
                jumpActivity(LivePlayActivity.class);
            }
        } else if (v.getId() == R.id.tvSearch) {
            if (ApiConfig.get().getSourceBeanList().isEmpty()) {
                Toast.makeText(mContext, "请在【设置】界面配置视频源地址", Toast.LENGTH_LONG).show();
                return;
            }
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.tvPush) {
            jumpActivity(PushActivity.class);
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        } else if (v.getId() == R.id.tvDouban) {
            Hawk.put(HawkConfig.HOME_REC, 3);
            homeHotVodAdapter.setTvRate("TOP");
            setDouBanTop250Data(homeHotVodAdapter, true);
        } else if (v.getId() == R.id.tvRecentHot) {
            Hawk.put(HawkConfig.HOME_REC, 0);
            homeHotVodAdapter.setTvRate("热播");
            setDouBanData(homeHotVodAdapter);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_CONNECTION) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
