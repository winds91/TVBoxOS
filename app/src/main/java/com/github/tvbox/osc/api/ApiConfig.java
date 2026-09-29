package com.github.tvbox.osc.api;

import android.text.TextUtils;

import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiConfig {
    private static ApiConfig instance;
    private final List<LiveChannelGroup> liveChannelGroupList;
    private final List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();
    private List<IJKCode> ijkCodes;
    private final Gson gson;
    private final Map<String, String> myHosts = new HashMap<>();

    private ApiConfig() {
        liveChannelGroupList = new ArrayList<>();
        gson = new Gson();
        initLiveSources();
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

    // 多源列表：url 为空表示内置源（读 assets/live_channels.txt 的写死数据源），
    // 后续切换数据源时往 LIVE_GROUP_LIST 里写入 {"name":..,"type":..,"url":..} 即可
    private void initLiveSources() {
        JsonArray liveSources = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        if (liveSources == null || liveSources.isEmpty()) {
            JsonObject builtInSource = new JsonObject();
            builtInSource.addProperty("name", "内置源");
            builtInSource.addProperty("type", "0");
            builtInSource.addProperty("url", "");
            liveSources = new JsonArray();
            liveSources.add(builtInSource);
            Hawk.put(HawkConfig.LIVE_GROUP_LIST, liveSources);
        }
        int index = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
        if (index < 0 || index >= liveSources.size()) {
            Hawk.put(HawkConfig.LIVE_GROUP_INDEX, 0);
        }
    }

    public JsonArray getLiveSources() {
        JsonArray liveSources = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        return liveSources == null ? new JsonArray() : liveSources;
    }

    // 写入数据源列表（接入别的数据源时调用），选中下标越界会自动回到第 1 个源
    public void setLiveSources(JsonArray liveSources) {
        if (liveSources == null || liveSources.isEmpty()) {
            return;
        }
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, liveSources);
        getLiveGroupIndex();
    }

    public String getLiveSourceName(JsonObject source, int index) {
        if (source != null && source.has("name")) {
            String name = source.get("name").getAsString().trim();
            if (!name.isEmpty()) return name;
        }
        return "线路" + (index + 1);
    }

    public String getLiveSourceUrl(JsonObject source) {
        if (source == null) return "";
        String url = source.has("url") ? source.get("url").getAsString().trim() : "";
        if (url.isEmpty() && source.has("api")) url = source.get("api").getAsString().trim();
        return url;
    }

    public int getLiveGroupIndex() {
        JsonArray liveSources = getLiveSources();
        int index = Hawk.get(HawkConfig.LIVE_GROUP_INDEX, 0);
        if (index < 0 || index >= liveSources.size()) {
            index = 0;
            Hawk.put(HawkConfig.LIVE_GROUP_INDEX, index);
        }
        return index;
    }

    public JsonObject getCurrentLiveSource() {
        JsonArray liveSources = getLiveSources();
        int index = getLiveGroupIndex();
        if (index >= liveSources.size()) return null;
        JsonElement element = liveSources.get(index);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public void clearChannels() {
        liveChannelGroupList.clear();
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
        JsonArray liveSources = getLiveSources();
        for (int i = 0; i < liveSources.size(); i++) {
            JsonElement element = liveSources.get(i);
            JsonObject source = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            yumItems.add(getLiveSourceName(source, i));
        }

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

    /**
     * 应用数据源配置并清空已加载的频道，返回 false 表示该数据源不可用（只支持 type 0/3 的直连地址）
     */
    public boolean applyLiveSource(JsonObject livesOBJ) {
        liveChannelGroupList.clear();
        if (livesOBJ == null) {
            Hawk.put(HawkConfig.LIVE_API_URL, "");
            return false;
        }
        try {
            String type = livesOBJ.has("type") ? livesOBJ.get("type").getAsString() : "0";
            if (!type.equals("0") && !type.equals("3")) {
                LOG.i("echo-live-source unsupported type:" + type);
                Hawk.put(HawkConfig.LIVE_API_URL, "");
                return false;
            }
            String url = getLiveSourceUrl(livesOBJ);
            LOG.i("echo-live-source url:" + url);
            // 每个数据源的频道配置分开存；内置源 url 为空，配置key与旧版本保持一致
            Hawk.put(HawkConfig.LIVE_API_URL, url);
            //设置epg
            if (livesOBJ.has("epg")) {
                Hawk.put(HawkConfig.EPG_URL, livesOBJ.get("epg").getAsString());
            } else {
                Hawk.put(HawkConfig.EPG_URL, "");
            }
            //直播播放器类型
            if (livesOBJ.has("playerType")) {
                try {
                    Hawk.put(HawkConfig.LIVE_PLAY_TYPE, Integer.parseInt(livesOBJ.get("playerType").getAsString().trim()));
                } catch (NumberFormatException e) {
                    LOG.e("echo-live-source invalid playerType:" + livesOBJ.get("playerType"));
                }
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
                Hawk.delete(HawkConfig.LIVE_WEB_HEADER);
            }
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
        return true;
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
            if (!foundOldSelect && !ijkCodes.isEmpty()) {
                ijkCodes.get(0).selected(true);
            }
        }
        LOG.i("echo-default-config-----------load");
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public Map<String, String> getMyHost() {
        return myHosts;
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
