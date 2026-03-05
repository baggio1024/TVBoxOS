package com.github.tvbox.osc.base;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
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

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;

    private static P2PClass p;
    public static String burl;
    private static String dashData;

    private Thread.UncaughtExceptionHandler mDefaultHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initCrashHandler();
        
        // 同步初始化 Hawk (配置存储库)
        Hawk.init(this).build();
        
        // 同步初始化 OkGo 网络库，防止 Activity 启动时 client 为 null 导致崩溃
        try {
            OkGoHelper.init();
        } catch (Throwable th) {
            LOG.e("OkGoHelper init error: " + th.getMessage());
        }

        ControlManager.init(this);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    initParams();
                } catch (Throwable th) {
                    LOG.e("initParams error: " + th.getMessage());
                }
            }
        }).start();
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    EpgUtil.init();
                } catch (Throwable th) {
                    LOG.e("EpgUtil init error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ControlManager.get().startServer();
                } catch (Throwable th) {
                    LOG.e("ControlManager startServer error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AppDataManager.init();
                } catch (Throwable th) {
                    LOG.e("AppDataManager init error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LoadSir.beginBuilder()
                            .addCallback(new EmptyCallback())
                            .addCallback(new LoadingCallback())
                            .commit();
                } catch (Throwable th) {
                    LOG.e("LoadSir init error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                            .setSupportDP(false)
                            .setSupportSP(false)
                            .setSupportSubunits(Subunits.MM);
                } catch (Throwable th) {
                    LOG.e("AutoSizeConfig init error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerHelper.init();
                } catch (Throwable th) {
                    LOG.e("PlayerHelper init error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    QuickJSLoader.init();
                } catch (Throwable th) {
                    LOG.e("QuickJSLoader init error: " + th.getMessage());
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileUtils.cleanPlayerCache();
                } catch (Throwable th) {
                    LOG.e("cleanPlayerCache error: " + th.getMessage());
                }
            }
        }).start();
    }

    private void initCrashHandler() {
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                ex.printStackTrace(pw);
                String stackTrace = sw.toString();
                LOG.e("UncaughtException: " + stackTrace);
                android.util.Log.e("TVBox-Crash", stackTrace);
                if (mDefaultHandler != null) {
                    mDefaultHandler.uncaughtException(thread, ex);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(1);
                }
            }
        });
    }

    private void initParams() {
        // Hawk 已经在同步代码块中初始化，这里补充逻辑
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 1);
        }
        
        // 设置默认配置
        if (!Hawk.contains(HawkConfig.HOME_REC_STYLE)) {
            Hawk.put(HawkConfig.HOME_REC_STYLE, true); // 首页多行默认开启
        }
        if (!Hawk.contains(HawkConfig.SEARCH_VIEW)) {
            Hawk.put(HawkConfig.SEARCH_VIEW, 1); // 搜索展示默认 缩略图 (1)
        }
        if (!Hawk.contains(HawkConfig.FAST_SEARCH_MODE)) {
            Hawk.put(HawkConfig.FAST_SEARCH_MODE, true); // 聚合搜索默认开启
        }
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JsLoader.destroy();
    }


    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public static P2PClass getp2p() {
        try {
            if (p == null) {
                p = new P2PClass(FileUtils.getExternalCachePath());
            }
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    public void setDashData(String data) {
        dashData = data;
    }
    public String getDashData() {
        return dashData;
    }
}
