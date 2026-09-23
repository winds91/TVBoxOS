package com.github.tvbox.osc.util;

import android.content.Context;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.player.ExoMediaPlayerFactory;
import com.github.tvbox.osc.player.IjkMediaPlayer;
import com.github.tvbox.osc.player.render.SurfaceRenderViewFactory;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;

import tv.danmaku.ijk.media.player.IjkLibLoader;
import xyz.doikki.videoplayer.player.AndroidMediaPlayerFactory;
import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.RenderViewFactory;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class PlayerHelper {

    public static void updateCfg(VideoView videoView, JSONObject playerCfg) {
        updateCfg(videoView, playerCfg, -1);
    }

    public static void updateCfg(VideoView videoView, JSONObject playerCfg, int forcePlayerType) {
        int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        String ijkCode = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
        int scale = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        try {
            playerType = playerCfg.getInt("pl");
            renderType = playerCfg.getInt("pr");
            ijkCode = playerCfg.getString("ijk");
            scale = playerCfg.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (forcePlayerType >= 0) playerType = forcePlayerType;
        applyPlayerConfig(videoView, playerType, renderType, ijkCode, scale);
    }

    public static void updateCfg(VideoView videoView) {
        int playType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        applyPlayerConfig(videoView, playType, renderType, Hawk.get(HawkConfig.IJK_CODEC, "硬解码"), Hawk.get(HawkConfig.PLAY_SCALE, 0));
    }

    private static void applyPlayerConfig(VideoView videoView, int playerType, int renderType, String ijkCode, int scale) {
        IJKCode codec = ApiConfig.get().getIJKCodec(ijkCode);
        PlayerFactory playerFactory;
        if (playerType == 1) {
            playerFactory = new PlayerFactory<IjkMediaPlayer>() {
                @Override
                public IjkMediaPlayer createPlayer(Context context) {
                    return new IjkMediaPlayer(context, codec);
                }
            };
            loadIjkLibrary();
        } else if (playerType == 2) {
            playerFactory = ExoMediaPlayerFactory.create();
        } else {
            playerFactory = AndroidMediaPlayerFactory.create();
        }
        RenderViewFactory renderViewFactory;
        switch (renderType) {
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
        }
        if (videoView != null) {
            videoView.setPlayerFactory(playerFactory);
            videoView.setRenderViewFactory(renderViewFactory);
            videoView.setScreenScaleType(scale);
        }
    }

    private static void loadIjkLibrary() {
        try {
            tv.danmaku.ijk.media.player.IjkMediaPlayer.loadLibrariesOnce(new IjkLibLoader() {
                @Override
                public void loadLibrary(String s) throws UnsatisfiedLinkError, SecurityException {
                    try {
                        System.loadLibrary(s);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void init() {
        loadIjkLibrary();
    }

    public static String getPlayerName(int playType) {
        switch (playType) {
            case 1:
                return "IJK播放器";
            case 2:
                return "Exo播放器";
            default:
                return "系统播放器";
        }
    }

    public static String getRenderName(int renderType) {
        if (renderType == 1) {
            return "SurfaceView";
        } else {
            return "TextureView";
        }
    }

    public static String getScaleName(int screenScaleType) {
        String scaleText = "默认";
        switch (screenScaleType) {
            case VideoView.SCREEN_SCALE_DEFAULT:
                scaleText = "默认";
                break;
            case VideoView.SCREEN_SCALE_16_9:
                scaleText = "16:9";
                break;
            case VideoView.SCREEN_SCALE_4_3:
                scaleText = "4:3";
                break;
            case VideoView.SCREEN_SCALE_MATCH_PARENT:
                scaleText = "填充";
                break;
            case VideoView.SCREEN_SCALE_ORIGINAL:
                scaleText = "原始";
                break;
            case VideoView.SCREEN_SCALE_CENTER_CROP:
                scaleText = "裁剪";
                break;
        }
        return scaleText;
    }

    public static String getDisplaySpeed(long speed, boolean show) {
        if (speed > 1048576)
            return new DecimalFormat("#.00").format(speed / 1048576d) + "Mb/s";
        else if (speed > 1024)
            return (speed / 1024) + "Kb/s";
        else
            return speed > 0 ? speed + "B/s" : (show ? "0B/s" : "");
    }
}
