package com.github.tvbox.osc.util.urlhttp;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by fighting on 2017/4/7.
 */

public abstract class CallBackUtil<T> {
    static Handler mMainHandler = new Handler(Looper.getMainLooper());


    public  void onProgress(float progress, long total ){}

    void onError(final RealResponse response){

        final String errorMessage;
        if(response.inputStream != null){
            errorMessage = getRetString(response.inputStream);
        }else if(response.errorStream != null) {
            errorMessage = getRetString(response.errorStream);
        }else if(response.exception != null) {
            errorMessage = response.exception.getMessage();
        }else {
            errorMessage = "";
        }
        mMainHandler.post(() -> onFailure(response.code,errorMessage));
    }
    void onSeccess(RealResponse response){
        final T obj = onParseResponse(response);
        mMainHandler.post(() -> onResponse(obj));
    }


    /**
     * 解析response，执行在子线程
     */
    public abstract T onParseResponse(RealResponse response);

    /**
     * 访问网络失败后被调用，执行在UI线程
     */
    public abstract void onFailure(int code,String errorMessage);

    /**
     *
     * 访问网络成功后被调用，执行在UI线程
     */
    public abstract void onResponse(T response);


    public static abstract class CallBackString extends CallBackUtil<String> {
        @Override
        public String onParseResponse(RealResponse response) {
            try {
                return getRetString(response.inputStream);
            } catch (Exception e) {
                throw new RuntimeException("failure");
            }
        }
    }


    private static String getRetString(InputStream is) {
        String buf;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
            StringBuilder sb = new StringBuilder();
            String line = "";
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            is.close();
            buf = sb.toString();
            return buf;

        } catch (Exception e) {
            return null;
        }
    }

}
