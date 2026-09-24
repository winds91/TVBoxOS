package com.github.tvbox.osc.base;

import android.app.Activity;

import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.orhanobut.hawk.NoEncryption;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

public class App extends MultiDexApplication {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initParams();
        // OKGo
        OkGoHelper.init();
        EpgUtil.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        FileUtils.cleanPlayerCache();
    }

    private void initParams() {
        // Hawk：显式使用无加密实现，避免加载 libconceal.so（部分老旧 ARMv7 设备会 SIGILL 崩溃）
        Hawk.init(this).setEncryption(new NoEncryption()).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        // 旧版本用 Conceal 加密存储，切换到无加密后旧值读不出来，按默认播放器(IJK)重建，避免退化成系统播放器
        if (Hawk.get(HawkConfig.PLAY_TYPE, null) == null) {
            Hawk.put(HawkConfig.PLAY_TYPE, 1);
        }
    }

    public static App getInstance() {
        return instance;
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }
}
