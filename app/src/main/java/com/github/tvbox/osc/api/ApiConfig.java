package com.github.tvbox.osc.api;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Base64;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.util.AES;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiConfig {
    private static ApiConfig instance;
    private final List<LiveChannelGroup> liveChannelGroupList;
    private final List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();
    private List<IJKCode> ijkCodes;
    private final Gson gson;
    private final Map<String, String> myHosts = new HashMap<>();

    private final String userAgent = "okhttp/3.15";
    private final String requestAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9";
    private final String TempKey = null;

    private ApiConfig() {
        liveChannelGroupList = new ArrayList<>();
        gson = new Gson();
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        loadDefaultConfig();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public static String FindResult(String json, String configKey) {
        String content = json;
        try {
            if (AES.isJson(content)) return content;
            Pattern pattern = getPattern("[A-Za-z0]{8}\\*\\*");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                content = content.substring(content.indexOf(matcher.group()) + 10);
                content = new String(Base64.decode(content, Base64.DEFAULT));
            }
            if (content.startsWith("2423")) {
                String data = content.substring(content.indexOf("2324") + 4, content.length() - 26);
                content = new String(AES.toBytes(content)).toLowerCase();
                String key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16);
                String iv = AES.rightPadding(content.substring(content.length() - 13), "0", 16);
                json = AES.CBC(data, key, iv);
            } else if (configKey != null && !AES.isJson(content)) {
                json = AES.ECB(content, configKey);
            } else {
                json = content;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private String configUrl(String apiUrl) {
        String configUrl;
        if (apiUrl.startsWith("clan")) {
            return apiUrl;
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://" + apiUrl;
        } else {
            configUrl = apiUrl;
        }
        return configUrl;
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");

        if (!liveApiUrl.isEmpty() && !liveApiUrl.equals(apiUrl)) {
            if (liveApiUrl.contains(".txt") || liveApiUrl.contains(".m3u")
                    || liveApiUrl.contains("=txt") || liveApiUrl.contains("=m3u")) {
                initLiveSettings();
                parseLiveJson(liveApiUrl, "{\"lives\":[{\"name\":\"直播\",\"type\":0,\"url\":\"" + liveApiUrl + "\"}]}");
                callback.success();
            } else {
                File live_cache = new File(App.getInstance().getFilesDir(), MD5.encode(liveApiUrl));
                if (useCache && live_cache.exists()) {
                    try {
                        parseLiveJson(liveApiUrl, live_cache);
                        callback.success();
                        return;
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                String liveApiConfigUrl = configUrl(liveApiUrl);
                OkGo.<String>get(liveApiConfigUrl)
                        .headers("User-Agent", userAgent)
                        .headers("Accept", requestAccept)
                        .execute(new AbsCallback<String>() {
                            @Override
                            public void onSuccess(Response<String> response) {
                                try {
                                    String json = response.body();
                                    parseLiveJson(liveApiUrl, json);
                                    FileUtils.saveCache(live_cache, json);
                                    callback.success();
                                } catch (Throwable th) {
                                    th.printStackTrace();
                                    callback.notice("解析直播配置失败");
                                }
                            }

                            @Override
                            public void onError(Response<String> response) {
                                super.onError(response);
                                if (live_cache.exists()) {
                                    try {
                                        parseLiveJson(liveApiUrl, live_cache);
                                        callback.success();
                                        return;
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                    }
                                }
                                callback.notice("直播配置拉取失败");
                            }

                            public String convertResponse(okhttp3.Response response) throws Throwable {
                                String result = "";
                                if (response.body() != null) {
                                    result = FindResult(response.body().string(), TempKey);
                                }
                                return result;
                            }
                        });
            }
            return;
        }

        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir(), MD5.encode(apiUrl));
        if (useCache && cache.exists()) {
            try {
                parseJson(apiUrl, cache);
                callback.success();
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String configUrl = configUrl(apiUrl);
        OkGo.<String>get(configUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String json = response.body();
                            parseJson(apiUrl, json);
                            FileUtils.saveCache(cache, json);
                            callback.success();
                        } catch (Throwable th) {
                            th.printStackTrace();
                            callback.error("解析配置失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (cache.exists()) {
                            try {
                                parseJson(apiUrl, cache);
                                callback.success();
                                return;
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                        callback.error("拉取配置失败\n" + (response.getException() != null ? response.getException().getMessage() : ""));
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() != null) {
                            result = FindResult(response.body().string(), TempKey);
                        }
                        return result;
                    }
                });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = bReader.readLine()) != null) {
            sb.append(s).append("\n");
        }
        bReader.close();
        parseJson(apiUrl, sb.toString());
    }

    private void parseJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        // 直播源
        String live_api_url = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (live_api_url.isEmpty() || apiUrl.equals(live_api_url)) {
            LOG.i("echo-load-config_live");
            initLiveSettings();
            if (infoJson.has("lives")) {
                JsonArray lives_groups = infoJson.get("lives").getAsJsonArray();
                int live_group_index = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
                if (live_group_index > lives_groups.size() - 1) Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
                Hawk.put(HawkConfig.LIVE_GROUP_LIST, lives_groups);
                //加载多源配置
                try {
                    ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
                    for (int i = 0; i < lives_groups.size(); i++) {
                        JsonObject jsonObject = lives_groups.get(i).getAsJsonObject();
                        String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "线路" + (i + 1);
                        LiveSettingItem liveSettingItem = new LiveSettingItem();
                        liveSettingItem.setItemIndex(i);
                        liveSettingItem.setItemName(name);
                        liveSettingItemList.add(liveSettingItem);
                    }
                    liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                JsonObject livesOBJ = lives_groups.get(Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0)).getAsJsonObject();
                loadLiveApi(livesOBJ);
            }
        }
        LOG.i("echo-api-config-----------load");
    }

    private void parseLiveJson(String apiUrl, File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = bReader.readLine()) != null) {
            sb.append(s).append("\n");
        }
        bReader.close();
        parseLiveJson(apiUrl, sb.toString());
    }

    private void parseLiveJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        initLiveSettings();
        if (infoJson.has("lives")) {
            JsonArray lives_groups = infoJson.get("lives").getAsJsonArray();
            int live_group_index = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
            if (live_group_index > lives_groups.size() - 1) Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
            Hawk.put(HawkConfig.LIVE_GROUP_LIST, lives_groups);
            try {
                ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
                for (int i = 0; i < lives_groups.size(); i++) {
                    JsonObject jsonObject = lives_groups.get(i).getAsJsonObject();
                    String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "线路" + (i + 1);
                    LiveSettingItem liveSettingItem = new LiveSettingItem();
                    liveSettingItem.setItemIndex(i);
                    liveSettingItem.setItemName(name);
                    liveSettingItemList.add(liveSettingItem);
                }
                liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
            } catch (Exception e) {
                e.printStackTrace();
            }
            JsonObject livesOBJ = lives_groups.get(Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0)).getAsJsonObject();
            loadLiveApi(livesOBJ);
        }
        LOG.i("echo-api-live-config-----------load");
    }

    public void initLiveSettings() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "多源切换"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        ArrayList<String> yumItems = new ArrayList<>();

        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);
        itemsArrayList.add(yumItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
    }

    public List<LiveSettingGroup> getLiveSettingGroupList() {
        return liveSettingGroupList;
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1)
                        sourceNames.add(splitText[1]);
                    else
                        sourceNames.add("源" + sourceIndex);
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    public void loadLiveApi(JsonObject livesOBJ) {
        try {
            LOG.i("echo-loadLiveApi");
            String type = livesOBJ.has("type") ? livesOBJ.get("type").getAsString() : "0";
            String url;
            if (type.equals("0") || type.equals("3")) {
                url = livesOBJ.has("url") ? livesOBJ.get("url").getAsString() : "";
                if (url.isEmpty()) url = livesOBJ.has("api") ? livesOBJ.get("api").getAsString() : "";
                LOG.i("echo-liveurl" + url);
            } else {
                liveChannelGroupList.clear();
                return;
            }
            //设置epg
            if (livesOBJ.has("epg")) {
                String epg = livesOBJ.get("epg").getAsString();
                Hawk.put(HawkConfig.EPG_URL, epg);
            } else {
                Hawk.put(HawkConfig.EPG_URL, "");
            }
            //直播播放器类型
            if (livesOBJ.has("playerType")) {
                String livePlayType = livesOBJ.get("playerType").getAsString();
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE, livePlayType);
            } else {
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE, Hawk.get(HawkConfig.PLAY_TYPE, 0));
            }
            //设置UA
            if (livesOBJ.has("header")) {
                JsonObject headerObj = livesOBJ.getAsJsonObject("header");
                HashMap<String, String> liveHeader = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : headerObj.entrySet()) {
                    liveHeader.put(entry.getKey(), entry.getValue().getAsString());
                }
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader);
            } else if (livesOBJ.has("ua")) {
                String ua = livesOBJ.get("ua").getAsString();
                HashMap<String, String> liveHeader = new HashMap<>();
                liveHeader.put("User-Agent", ua);
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader);
            } else {
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, null);
            }
            // 直接存储频道列表URL，不使用本地代理
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setGroupName(url);
            liveChannelGroupList.clear();
            liveChannelGroupList.add(liveChannelGroup);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void loadDefaultConfig() {
        String defaultIJKADS = "{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"硬解码\"}]}";
        JsonObject defaultJson = gson.fromJson(defaultIJKADS, JsonObject.class);
        if (ijkCodes == null) {
            ijkCodes = new ArrayList<>();
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
            JsonArray ijkJsonArray = defaultJson.get("ijk").getAsJsonArray();
            for (JsonElement opt : ijkJsonArray) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                ijkCodes.add(codec);
            }
            if (!foundOldSelect && ijkCodes.size() > 0) {
                ijkCodes.get(0).selected(true);
            }
        }
        LOG.i("echo-default-config-----------load");
    }

    public interface LoadConfigCallback {
        void success();

        void error(String msg);

        void notice(String msg);
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public Map<String, String> getMyHost() {
        return myHosts;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return ijkCodes.get(0);
    }
}
