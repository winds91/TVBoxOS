package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.File;
import java.io.FileOutputStream;

public class FileUtils {

    public static String getCachePath() {
        return App.getInstance().getCacheDir().getAbsolutePath();
    }

    public static void cleanDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return;
        for (File one : files) {
            try {
                deleteFile(one);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void deleteFile(File file) {
        if (!file.exists()) return;
        if (file.isFile()) {
            if (file.canWrite()) file.delete();
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null || files.length == 0) {
                if (file.canWrite()) file.delete();
                return;
            }
            for (File one : files) {
                deleteFile(one);
            }
        }
    }

    public static void cleanPlayerCache() {
        String ijkCachePath = getCachePath() + "/ijkcaches/";
        File ijkCacheDir = new File(ijkCachePath);
        try {
            if (ijkCacheDir.exists()) cleanDirectory(ijkCacheDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveCache(File cache, String json) {
        try {
            File cacheDir = cache.getParentFile();
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            if (cache.exists())
                cache.delete();
            FileOutputStream fos = new FileOutputStream(cache);
            fos.write(json.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
