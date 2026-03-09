package com.github.tvbox.osc.base;

import android.app.Activity;
import android.os.Process;
import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;
import com.whl.quickjs.android.QuickJSLoader;
import com.github.catvod.crawler.JsLoader;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description: 优化启动序列与线程管理
 */
public class App extends MultiDexApplication {
    private static App instance;
    private static P2PClass p;
    public static String burl;
    private static String dashData;
    private Thread.UncaughtExceptionHandler mDefaultHandler;

    // 全局单线程池，用于处理应用初始化任务，避免创建大量零散线程
    private static final ExecutorService initExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initCrashHandler();
        
        // 1. 核心同步初始化 (必须在主线程第一时间完成)
        Hawk.init(this).build();
        try {
            OkGoHelper.init();
            ControlManager.init(this);
            // 强制主线程初始化播放器核心，防止播放时 So 库未就绪
            PlayerHelper.init(); 
        } catch (Throwable th) {
            LOG.e("Core init error: " + th.getMessage());
        }

        // 2. 异步初始化任务 (合并执行，减少线程切换开销)
        initExecutor.execute(() -> {
            try {
                initParams();
                EpgUtil.init();
                ControlManager.get().startServer();
                AppDataManager.init();
                QuickJSLoader.init();
                FileUtils.cleanPlayerCache();
                
                // UI 相关非阻塞组件
                LoadSir.beginBuilder()
                        .addCallback(new EmptyCallback())
                        .addCallback(new LoadingCallback())
                        .commit();
                
                AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                        .setSupportDP(false)
                        .setSupportSP(false)
                        .setSupportSubunits(Subunits.MM);
            } catch (Throwable th) {
                LOG.e("Async init error: " + th.getMessage());
            }
        });
    }

    private void initCrashHandler() {
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTrace = sw.toString();
            LOG.e("UncaughtException: " + stackTrace);
            if (mDefaultHandler != null) {
                mDefaultHandler.uncaughtException(thread, ex);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(1);
            }
        });
    }

    private void initParams() {
        // 设置默认值
        if (!Hawk.contains(HawkConfig.DEBUG_OPEN)) Hawk.put(HawkConfig.DEBUG_OPEN, false);
        
        // 播放器默认为 IJK (1)
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) Hawk.put(HawkConfig.PLAY_TYPE, 1);
        
        // IJK 缓存默认开启 (true)
        if (!Hawk.contains(HawkConfig.IJK_CACHE_PLAY)) Hawk.put(HawkConfig.IJK_CACHE_PLAY, true);
        
        // 聚合搜索默认关闭 (false)
        if (!Hawk.contains(HawkConfig.FAST_SEARCH_MODE)) Hawk.put(HawkConfig.FAST_SEARCH_MODE, false);
        
        // 首页多行默认开启 (true)
        if (!Hawk.contains(HawkConfig.HOME_REC_STYLE)) Hawk.put(HawkConfig.HOME_REC_STYLE, true);
        
        // 去广告默认开启 (true)
        if (!Hawk.contains(HawkConfig.M3U8_PURIFY)) Hawk.put(HawkConfig.M3U8_PURIFY, true);

        if (!Hawk.contains(HawkConfig.SEARCH_VIEW)) Hawk.put(HawkConfig.SEARCH_VIEW, 1);
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JsLoader.destroy();
        if (!initExecutor.isShutdown()) {
            initExecutor.shutdown();
        }
    }

    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){ this.vodInfo = vodinfo; }
    public VodInfo getVodInfo(){ return this.vodInfo; }

    public static P2PClass getp2p() {
        try {
            if (p == null) p = new P2PClass(FileUtils.getExternalCachePath());
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    public Activity getCurrentActivity() { return AppManager.getInstance().currentActivity(); }
    public void setDashData(String data) { dashData = data; }
    public String getDashData() { return dashData; }
}
