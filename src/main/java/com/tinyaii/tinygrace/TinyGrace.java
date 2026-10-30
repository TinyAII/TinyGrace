package com.tinyaii.tinygrace;

import com.tinyaii.tinygrace.command.GraceCommand;
import com.tinyaii.tinygrace.config.ConfigManager;
import com.tinyaii.tinygrace.data.GraceData;
import com.tinyaii.tinygrace.listener.PlayerDeathListener;
import com.tinyaii.tinygrace.listener.UpdateNotifyListener;
import com.tinyaii.tinygrace.service.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TinyGrace —— 有限死亡保护。
 *
 * <p>前 N 次死亡保留背包与经验，用完之后恢复正常掉落。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>单 jar 支持 1.14 ~ 26.x</b>：用 1.14.4 的 API 编译（PDC 从 1.14 才有），
 *       编译目标 Java 8</li>
 *   <li><b>数据存玩家自身</b>（PersistentDataContainer），不建任何数据文件，
 *       换服/换存档时数据跟着玩家走</li>
 *   <li><b>零依赖</b>：不需要任何前置插件</li>
 * </ul>
 */
public final class TinyGrace extends JavaPlugin {

    public static final String VERSION = "1.0.0";

    private ConfigManager configManager;
    private GraceData graceData;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.reload();

        graceData = new GraceData(this, configManager);

        getServer().getPluginManager().registerEvents(
                new PlayerDeathListener(configManager, graceData), this);

        updateChecker = new UpdateChecker(this, configManager);
        updateChecker.start();
        getServer().getPluginManager().registerEvents(
                new UpdateNotifyListener(this, updateChecker), this);

        PluginCommand command = getCommand("tinygrace");
        if (command != null) {
            GraceCommand handler = new GraceCommand(this, configManager, graceData);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("plugin.yml 里找不到 tinygrace 命令，指令功能不可用");
        }

        printBanner();
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stop();
        }
        // 数据存在玩家 PDC 里，由服务端负责落盘，这里不需要清理
    }

    /**
     * 当前生效版本。读 plugin.yml 而不是用常量，避免两者不同步。
     */
    public String currentVersion() {
        try {
            String v = getDescription().getVersion();
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        } catch (Throwable ignored) {
            // 极新核心可能移除该方法
        }
        return VERSION;
    }

    private void printBanner() {
        getLogger().info("========================================");
        getLogger().info(" TinyGrace v" + currentVersion() + " —— 有限死亡保护");
        getLogger().info(" 支持范围: 1.14 ~ 26.x（Java 8 字节码, api-version 1.14）");
        getLogger().info(" 全局开关: " + (configManager.isEnabled() ? "开启" : "关闭"));
        getLogger().info(" 基础保护: " + configManager.getBaseProtections() + " 次"
                + " | 新玩家额外: " + configManager.getDefaultBonus() + " 次");
        getLogger().info(" 临界预警: " + (configManager.getWarnAt().isEmpty()
                ? "关闭" : configManager.getWarnAt().toString()));
        getLogger().info(" keepInventory 世界: "
                + (configManager.isKeepInventoryConsumes() ? "仍消耗配额" : "不消耗配额"));
        getLogger().info("========================================");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GraceData getGraceData() {
        return graceData;
    }
}
