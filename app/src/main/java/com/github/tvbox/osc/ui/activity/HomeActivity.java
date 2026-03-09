package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private final Handler mHandler = new Handler();
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        Log.i("SpeedLog", "[Activity] HomeActivity init 开始");
        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
        mHandler.post(mRunnable);
    }

    private void initView() {
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    mGridView.setSelectedPosition(0);
                    mGridView.scrollToPosition(0);
                    View targetChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (targetChild != null) {
                        targetChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = view.findViewById(R.id.tvTitle);
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_BBFFFFFF));
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }

                        public final int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (sortData != null && sortData.filters != null && !sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if ((baseLazyFragment instanceof GridFragment) && sortData != null && !sortData.filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener((direction, view) -> {
            if (direction == View.FOCUS_UP) {
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if ((baseLazyFragment instanceof GridFragment)) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
            if (direction != View.FOCUS_DOWN) {
                return false;
            }
            BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
            if (!(baseLazyFragment instanceof GridFragment)) {
                return false;
            }
            return !((GridFragment) baseLazyFragment).isLoad();
        });
        tvName.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            if(dataInitOk && jarInitOk){
                // 执行全量清理
                SourceViewModel.clearAllCache(); // 清理内存缓存
                Hawk.deleteAll(); // 清理所有本地存储
                
                String cspCachePath = FileUtils.getFilePath()+"/csp/";
                String jar=ApiConfig.get().getHomeSourceBean().getJar();
                String jarUrl=!jar.isEmpty()?jar:ApiConfig.get().getSpider();
                File cspCacheDir = new File(cspCachePath + MD5.string2MD5(jarUrl)+".jar");
                
                new Thread(() -> {
                    try {
                        if (cspCacheDir.exists()) {
                            FileUtils.deleteFile(cspCacheDir);
                        }
                        ApiConfig.get().clearJarLoader();
                        mHandler.post(() -> {
                            Toast.makeText(mContext, "缓存及分类数据已清除", Toast.LENGTH_LONG).show();
                            refreshHome();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        mHandler.post(() -> refreshHome());
                    }
                }).start();

            }else {
                jumpActivity(SettingActivity.class);
            }
        });
        tvName.setOnLongClickListener(v -> {
            jumpActivity(SettingActivity.class);
            return true;
        });
        setLoadSir(this.contentLayout);
    }

    private void showFilterIcon(int count) {
        if (sortFocusView != null) {
            View tvFilter = sortFocusView.findViewById(R.id.tvFilter);
            View tvFilterColor = sortFocusView.findViewById(R.id.tvFilterColor);
            if (tvFilter != null && tvFilterColor != null) {
                if (count > 0) {
                    tvFilter.setVisibility(View.GONE);
                    tvFilterColor.setVisibility(View.VISIBLE);
                } else {
                    tvFilter.setVisibility(View.VISIBLE);
                    tvFilterColor.setVisibility(View.GONE);
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            showFilterIcon((int) event.obj);
        }
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                }
            }
        }
    };

    private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, absXml -> {
            if (skipNextUpdate) {
                skipNextUpdate = false;
                return;
            }
            showSuccess();
            String sourceKey = ApiConfig.get().getHomeSourceBean().getKey();
            
            // 彻底还原逻辑：移除之前的 type_pid != 0 物理拦截，允许所有分类显示
            if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                // 不再进行 Iterator 物理移除，由 DefaultConfig.adjustSort 统一处理敏感词过滤
                sortAdapter.setNewData(DefaultConfig.adjustSort(sourceKey, absXml.classes.sortList, true));
            } else {
                sortAdapter.setNewData(DefaultConfig.adjustSort(sourceKey, new ArrayList<>(), true));
            }

            initViewPager(absXml);
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home != null && home.getName() != null && !home.getName().isEmpty()) tvName.setText(home.getName());
            tvName.clearAnimation();
            Log.i("SpeedLog", "[最终完成] 首页数据展示总耗时: " + (System.currentTimeMillis() - startTotalTime) + "ms");
        });
    }

    private long startTotalTime = 0;
    private void initData() {
        if (startTotalTime == 0) startTotalTime = System.currentTimeMillis();
        SourceBean homeSource = ApiConfig.get().getHomeSourceBean();
        String sourceKey = (homeSource != null) ? homeSource.getKey() : "";
        
        Log.i("SpeedLog", "[Activity] initData 被调用, dataInitOk=" + dataInitOk + ", jarInitOk=" + jarInitOk + ", source=" + sourceKey);

        if (dataInitOk && jarInitOk) {
            if (sourceKey == null) sourceKey = "";
            sourceViewModel.getSort(sourceKey);
            return;
        }

        if (dataInitOk && (homeSource != null && homeSource.getType() != 3)) {
            Log.i("SpeedLog", "[Activity] 主站非JS站点，开始并行预加载分类");
            sourceViewModel.getSort(sourceKey);
            checkAndLoadJar();
            return;
        }

        tvNameAnimation();
        showLoading();
        
        if (!dataInitOk) {
            Log.i("SpeedLog", "[Activity] 发起 loadConfig");
            ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
                @Override public void notice(String msg) { mHandler.post(() -> Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show()); }
                @Override public void success() {
                    dataInitOk = true;
                    initData(); 
                }
                @Override public void error(String msg) {
                    showSuccess();
                    if (msg.equalsIgnoreCase("-1")) {
                        dataInitOk = true; jarInitOk = true; initData();
                        return;
                    }
                    mHandler.post(() -> {
                        Toast.makeText(HomeActivity.this, "配置加载失败！", Toast.LENGTH_LONG).show();
                        TipDialog dialog = new TipDialog(HomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                            @Override public void left() { mHandler.post(() -> { initData(); }); }
                            @Override public void right() { dataInitOk = true; jarInitOk = true; initData(); }
                            @Override public void cancel() { dataInitOk = true; jarInitOk = true; initData(); }
                        });
                        dialog.show();
                    });
                }
            }, this);
        } else {
            checkAndLoadJar();
        }
    }

    private void checkAndLoadJar() {
        String spider = ApiConfig.get().getSpider();
        if (spider != null && !spider.isEmpty() && !jarInitOk) {
            Log.i("SpeedLog", "[Activity] 发起 loadJar: " + spider);
            ApiConfig.get().loadJar(useCacheConfig, spider, new ApiConfig.LoadConfigCallback() {
                @Override public void success() {
                    jarInitOk = true;
                    SourceBean homeSource = ApiConfig.get().getHomeSourceBean();
                    if (homeSource != null && homeSource.getType() == 3) {
                        initData();
                    }
                }
                @Override public void notice(String msg) {}
                @Override public void error(String msg) {
                    jarInitOk = true; 
                    initData();
                }
            });
        } else {
            jarInitOk = true;
        }
    }

    private void initViewPager(AbsSortXml absXml) {
        fragments.clear();
        if (!sortAdapter.getData().isEmpty()) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    fragments.add(UserFragment.newInstance(absXml != null ? absXml.videoList : null));
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            HomePageAdapter pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            
            currentSelected = 0;
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        EventBus.getDefault().unregister(this);
        AppManager.getInstance().finishActivity(this);
        ControlManager.get().stopServer();
    }

    private SelectDialog<SourceBean> mSiteSwitchDialog;

    void showSiteSwitch() {
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        
        mSiteSwitchDialog = new SelectDialog<>(HomeActivity.this);
        TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
        int spanCount = Math.min((int) Math.floor(sites.size() / 20.0), 2);
        tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount + 1));
        mSiteSwitchDialog.setTip("请选择首页数据源");
        
        mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                if (mSiteSwitchDialog != null && mSiteSwitchDialog.isShowing()) {
                    mSiteSwitchDialog.dismiss();
                }
                ApiConfig.get().setSourceBean(value);
                refreshHome();
            }
            @Override public String getDisplay(SourceBean val) { return val.getName(); }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) { return oldItem == newItem; }
            @Override public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) { 
                return oldItem.getKey() != null && oldItem.getKey().equals(newItem.getKey()); 
            }
        }, sites, select);
        mSiteSwitchDialog.show();
    }

    private void refreshHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        startActivity(intent);
        finish();
    }

    private void tvNameAnimation() {
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
}
