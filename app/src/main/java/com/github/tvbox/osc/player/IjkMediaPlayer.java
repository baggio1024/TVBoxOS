package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AudioTrackMemory;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import xyz.doikki.videoplayer.ijk.IjkPlayer;

public class IjkMediaPlayer extends IjkPlayer {

    private IJKCode codec = null;
    protected String currentPlayPath;
    private static AudioTrackMemory memory;

    public IjkMediaPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
        memory = AudioTrackMemory.getInstance(context);
    }

    @Override
    public void setOptions() {
        super.setOptions();
        IJKCode codecTmp = this.codec == null ? ApiConfig.get().getCurrentIJKCode() : this.codec;
        LinkedHashMap<String, String> options = codecTmp.getOption();
        if (options != null) {
            for (String key : options.keySet()) {
                String value = options.get(key);
                String[] opt = key.split("\\|");
                if (opt.length < 2) continue;
                int category = Integer.parseInt(opt[0].trim());
                String name = opt[1].trim();
                try {
                    assert value != null;
                    long valLong = Long.parseLong(value);
                    mMediaPlayer.setOption(category, name, valLong);
                } catch (Exception e) {
                    mMediaPlayer.setOption(category, name, value);
                }
            }
        }
        
        // --- [极速模式优化：极致吞吐与缓冲平衡] ---
        
        // 1. 探测与解析加速 (降低起播延迟)
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1000000); 
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 256); 
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);

        // 2. 缓冲策略 (8分钟极速抢跑)
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1); 
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 0); 
        
        // 150MB 极大粮仓：在 2Mbps 下提供约 8 分钟的抗冲击能力
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 1024 * 1024 * 150); 
        // 预加载时间：480秒 (8分钟)
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "buffer-range-ms", 480000); 
        
        // 起播门槛调优：维持 50 帧确保 2Mbps 环境下的初次起播稳定性
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 50); 
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "high-water-mark-ms", 5000); 
        
        // 3. 极速Seek优化
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "seek-at-start", 1);
        
        // 4. 视频硬解与渲染性能
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0); 
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1); 
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        
        // 5. 网络层加固
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 1);
        mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);

        if(Hawk.get(HawkConfig.PLAYER_IS_LIVE, false)){
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
        }
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        if (headers == null) headers = new HashMap<>();
        if (!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");
        }
        
        setDataSourceHeader(headers);
        currentPlayPath = path;
        super.setDataSource(path, null);
    }

    private void setDataSourceHeader(Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
            mMediaPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", sb.toString());
        }
    }

    public TrackInfo getTrackInfo() {
        try {
            IjkTrackInfo[] trackInfo = mMediaPlayer.getTrackInfo();
            if (trackInfo == null) return null;
            TrackInfo data = new TrackInfo();
            int index = 0;
            for (IjkTrackInfo info : trackInfo) {
                TrackInfoBean bean = new TrackInfoBean();
                bean.name = info.getInfoInline();
                bean.index = index++;
                if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) data.addAudio(bean);
                else if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) data.addSubtitle(bean);
            }
            return data;
        } catch (Throwable e) { return null; }
    }

    public void setTrack(int trackIndex) {
        try { mMediaPlayer.selectTrack(trackIndex); } catch (Throwable ignored) {}
    }

    public void setTrack(int trackIndex, String playKey) {
        if (!TextUtils.isEmpty(playKey)) {
            memory.save(playKey, trackIndex);
        }
        setTrack(trackIndex);
    }

    public void loadDefaultTrack(TrackInfo trackInfo, String playKey) {
        if (trackInfo != null && trackInfo.getAudio() != null && !trackInfo.getAudio().isEmpty()) {
            Integer trackIndex = memory.ijkLoad(playKey);
            if (trackIndex != -1) {
                setTrack(trackIndex);
            }
        }
    }

    public void setOnTimedTextListener(IMediaPlayer.OnTimedTextListener listener) {
        mMediaPlayer.setOnTimedTextListener(listener);
    }
}
