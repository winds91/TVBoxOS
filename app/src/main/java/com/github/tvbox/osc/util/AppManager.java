package com.github.tvbox.osc.util;

import android.app.Activity;

import java.util.Stack;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class AppManager {
    private static Stack<Activity> activityStack;

    private AppManager() {
    }

    private static class SingleHolder {
        private static final AppManager instance = new AppManager();
    }

    public static AppManager getInstance() {
        return SingleHolder.instance;
    }

    /**
     * 添加Activity到堆栈
     */
    public void addActivity(Activity activity) {
        if (activityStack == null) {
            activityStack = new Stack<>();
        }
        activityStack.add(activity);
    }

    /**
     * 获取当前Activity（堆栈中最后一个压入的）
     */
    public Activity currentActivity() {
        return activityStack.lastElement();
    }

    public void finishActivity(Activity activity) {
        activityStack.remove(activity);
    }
}