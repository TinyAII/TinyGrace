package com.tinyaii.tinygrace.config;

import com.tinyaii.tinygrace.TinyGrace;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置读取。所有值在 {@link #reload()} 时缓存成基本类型，
 * 运行期不再反复查 FileConfiguration。
 */
public final class ConfigManager {

    /** 版本检查服务的默认地址与 app_key（公开标识，不是密钥）。 */
    private static final String DEFAULT_UPDATE_ENDPOINT = "https://bump.tinyaii.cn";
    private static final String DEFAULT_APP_KEY = "app_bea0897e0b7064cffd866055";

    private final TinyGrace plugin;

    private boolean enabled = true;

    // 保护
    private int baseProtections = 10;
    private int defaultBonus = 0;
    private List<Integer> warnAt = Collections.emptyList();
    private boolean keepInventoryConsumes = false;
    private boolean keepLevel = true;
    private boolean warnExhausted = true;
    // 在线更新检查
    private boolean updateCheckEnabled = true;
    private String updateEndpoint = DEFAULT_UPDATE_ENDPOINT;
    private String updateAppKey = DEFAULT_APP_KEY;
    private String updateChannel = "stable";
    private int updateTimeoutSeconds = 3;
    private int updateIntervalHours = 6;
    private String updateNotifyPermission = "tinygrace.admin";

    public ConfigManager(TinyGrace plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        enabled = c.getBoolean("enabled", true);

        baseProtections = Math.max(0, c.getInt("protection.base", 10));
        defaultBonus = Math.max(0, c.getInt("protection.default-bonus", 0));
        keepInventoryConsumes = c.getBoolean("protection.keep-inventory-consumes", false);
        keepLevel = c.getBoolean("protection.keep-level", true);
        warnExhausted = c.getBoolean("protection.warn-exhausted", true);

        updateCheckEnabled = c.getBoolean("update-check.enabled", true);
        updateEndpoint = c.getString("update-check.endpoint", DEFAULT_UPDATE_ENDPOINT);
        updateAppKey = c.getString("update-check.app-key", "");
        // 老版本升级上来的 config.yml 没有 update-check 段；
        // Paper 上 setDefaults 兜不住（TinyFish 踩过），所以这里做代码级兜底。
        // app_key 是公开标识，写死在代码里没有安全风险。
        if (updateAppKey == null || updateAppKey.trim().isEmpty()) {
            updateAppKey = DEFAULT_APP_KEY;
        }
        updateChannel = c.getString("update-check.channel", "stable");
        updateTimeoutSeconds = Math.max(1, c.getInt("update-check.timeout-seconds", 3));
        updateIntervalHours = Math.max(1, c.getInt("update-check.interval-hours", 6));
        updateNotifyPermission = c.getString("update-check.notify-permission", "tinygrace.admin");

        List<Integer> warn = new ArrayList<Integer>();
        for (Object raw : c.getList("protection.warn-at", Collections.singletonList(0))) {
            if (raw instanceof Number) {
                int value = ((Number) raw).intValue();
                if (value > 0 && !warn.contains(value)) {
                    warn.add(value);
                }
            }
        }
        warnAt = Collections.unmodifiableList(warn);
    }

    // ── getter ──

    public boolean isEnabled() { return enabled; }

    public int getBaseProtections() { return baseProtections; }

    public int getDefaultBonus() { return defaultBonus; }

    public List<Integer> getWarnAt() { return warnAt; }

    public boolean isKeepInventoryConsumes() { return keepInventoryConsumes; }

    public boolean isKeepLevel() { return keepLevel; }

    /** 配额用完后的死亡是否红色提醒。 */
    public boolean isWarnExhausted() { return warnExhausted; }

    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }

    public String getUpdateEndpoint() { return updateEndpoint; }

    public String getUpdateAppKey() { return updateAppKey; }

    public String getUpdateChannel() { return updateChannel; }

    public int getUpdateTimeoutSeconds() { return updateTimeoutSeconds; }

    public int getUpdateIntervalHours() { return updateIntervalHours; }

    public String getUpdateNotifyPermission() { return updateNotifyPermission; }

    /** 取消息模板（未上色，交给 Texts.format 处理）。 */
    public String message(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    public List<String> messageList(String key) {
        return plugin.getConfig().getStringList("messages." + key);
    }

    /**
     * 运行时切换全局开关，并写回 config.yml。
     *
     * <p>用逐行替换而不是 {@code saveConfig()} —— 后者会把整份文件的注释吃掉。
     */
    public void setEnabled(boolean value) {
        this.enabled = value;
        plugin.getConfig().set("enabled", value);
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
            if (!com.tinyaii.tinygrace.util.ConfigWriter.replaceLine(
                    file, "enabled:", "enabled: " + value)) {
                plugin.getLogger().warning("未能写回 enabled 配置项，下次重启可能恢复为配置文件中的值");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("写回 config.yml 失败: " + e.getMessage());
        }
    }
}
