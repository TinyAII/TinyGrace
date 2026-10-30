package com.tinyaii.tinygrace.data;

import com.tinyaii.tinygrace.TinyGrace;
import com.tinyaii.tinygrace.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 玩家保护数据（存玩家自身的 PersistentDataContainer）。
 *
 * <p>四个键：
 * <ul>
 *   <li>{@code deaths}  —— 累计死亡次数，<b>只用于统计展示</b>，不参与判定</li>
 *   <li>{@code used}    —— 已消耗的保护次数，<b>判定全靠它</b></li>
 *   <li>{@code bonus}   —— 管理员额外发放的保护次数</li>
 *   <li>{@code enabled} —— 玩家个人开关</li>
 * </ul>
 *
 * <p>为什么把「死亡」和「已消耗」分开：如果只用一个计数器，配额用完之后它还会继续涨，
 * 管理员哪天把基础次数调高，老玩家会突然多出（新配额 − 旧死亡数）次保护 —— 那不是预期行为。
 * 分开之后：配额消耗完就不再变，统计值继续正常累加。
 */
public final class GraceData {

    private final TinyGrace plugin;
    private final ConfigManager config;

    private final NamespacedKey keyDeaths;
    private final NamespacedKey keyUsed;
    private final NamespacedKey keyBonus;
    private final NamespacedKey keyEnabled;

    public GraceData(TinyGrace plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.keyDeaths = new NamespacedKey(plugin, "deaths");
        this.keyUsed = new NamespacedKey(plugin, "used");
        this.keyBonus = new NamespacedKey(plugin, "bonus");
        this.keyEnabled = new NamespacedKey(plugin, "enabled");
    }

    private PersistentDataContainer pdc(Player player) {
        return player.getPersistentDataContainer();
    }

    // ────────────────────────── 读 ──────────────────────────

    public int getDeaths(Player player) {
        return pdc(player).getOrDefault(keyDeaths, PersistentDataType.INTEGER, 0);
    }

    public int getUsed(Player player) {
        return pdc(player).getOrDefault(keyUsed, PersistentDataType.INTEGER, 0);
    }

    public int getBonus(Player player) {
        return pdc(player).getOrDefault(keyBonus, PersistentDataType.INTEGER, config.getDefaultBonus());
    }

    /** 个人开关，默认开。 */
    public boolean isEnabled(Player player) {
        Byte value = pdc(player).get(keyEnabled, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    // ────────────────────────── 写 ──────────────────────────

    public void setDeaths(Player player, int value) {
        pdc(player).set(keyDeaths, PersistentDataType.INTEGER, Math.max(0, value));
    }

    public void setUsed(Player player, int value) {
        pdc(player).set(keyUsed, PersistentDataType.INTEGER, Math.max(0, value));
    }

    public void setBonus(Player player, int value) {
        pdc(player).set(keyBonus, PersistentDataType.INTEGER, Math.max(0, value));
    }

    public void setEnabled(Player player, boolean value) {
        pdc(player).set(keyEnabled, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    // ────────────────────────── 计算 ──────────────────────────

    /** 总保护次数 = 基础 + 额外。 */
    public int total(Player player) {
        return config.getBaseProtections() + getBonus(player);
    }

    /** 剩余保护次数（不会小于 0）。 */
    public int remaining(Player player) {
        return Math.max(0, total(player) - getUsed(player));
    }

    // ────────────────────────── 业务 ──────────────────────────

    /** 记一次死亡（只影响统计值）。 */
    public void recordDeath(Player player) {
        setDeaths(player, getDeaths(player) + 1);
    }

    /**
     * 尝试消耗一次保护配额。
     *
     * @return true = 消耗成功（本次应当保护）；false = 配额已用完
     */
    public boolean consume(Player player) {
        if (remaining(player) <= 0) {
            return false;
        }
        setUsed(player, getUsed(player) + 1);
        return true;
    }

    /**
     * 管理员把基础配额调整后，允许把已消耗归零重来（用于「重新发放保护」）。
     */
    public void resetUsage(Player player) {
        setUsed(player, 0);
    }
}
