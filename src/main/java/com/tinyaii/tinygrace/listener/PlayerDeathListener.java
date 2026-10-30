package com.tinyaii.tinygrace.listener;

import com.tinyaii.tinygrace.config.ConfigManager;
import com.tinyaii.tinygrace.data.GraceData;
import com.tinyaii.tinygrace.util.Texts;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 死亡处理：有配额就保留背包与经验，并扣一次配额。
 *
 * <p>判定顺序（顺序很重要，乱掉会出现「本来就不掉还扣次数」这类问题）：
 * <ol>
 *   <li>全局开关关 → 不管</li>
 *   <li>所在世界已开 keepInventory 且配置为不消耗 → 不管（本来就不会掉）</li>
 *   <li>玩家个人开关关 → 不管</li>
 *   <li>记一次死亡（统计）</li>
 *   <li>有 unlimited 权限 → 直接保护，不扣配额</li>
 *   <li>扣配额失败（用完了）→ 不管，正常掉落</li>
 * </ol>
 */
public final class PlayerDeathListener implements Listener {

    /** 权限：无限保护，不消耗配额。 */
    public static final String PERM_UNLIMITED = "tinygrace.unlimited";

    private final ConfigManager config;
    private final GraceData data;

    public PlayerDeathListener(ConfigManager config, GraceData data) {
        this.config = config;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!config.isEnabled()) {
            return;
        }
        if (skipByGameRule(player)) {
            return;
        }
        if (!data.isEnabled(player)) {
            return;
        }

        // 不论保不保护，死亡次数都要记 —— 这是统计值，管理员要用来看活跃度
        data.recordDeath(player);

        if (player.hasPermission(PERM_UNLIMITED)) {
            protect(event);
            return;
        }

        if (!data.consume(player)) {
            // 配额已用完：这次正常掉落。要明确告诉玩家「为什么掉东西了」，
            // 否则他只会在下一次才发现保护没了。
            warnExhausted(player);
            return;
        }

        protect(event);
        warnIfNeeded(player);
    }

    /** 配额耗尽时的红色提醒。 */
    private void warnExhausted(Player player) {
        if (!config.isWarnExhausted()) {
            return;
        }
        String template = config.message("exhausted");
        if (template == null || template.trim().isEmpty()) {
            // 老版本升级上来的 config.yml 里没有这条消息。
            // 不能因为配置缺项就静默 —— 那等于把提醒功能关掉了。
            template = "&c✖ 你的死亡保护次数已用完，本次死亡掉落了物品 &7(用 /tg info 查看)";
        }
        player.sendMessage(Texts.format(template, data.getDeaths(player)));
    }

    /**
     * 世界本身已开 keepInventory 时，默认不消耗玩家配额。
     *
     * <p>⚠️ {@code GameRule.KEEP_INVENTORY} 在 26.3 已被标记 <b>removal</b>（未来会删）。
     * 这里用 try-catch 兜住：取不到规则就按「继续处理」走 ——
     * 代价是那个版本之后「keepInventory 世界豁免」失效（每次死亡都会消耗配额），
     * 但绝不会崩。等 Paper 给出稳定的替代 API 再改。
     */
    private boolean skipByGameRule(Player player) {
        if (config.isKeepInventoryConsumes()) {
            return false;
        }
        World world = player.getWorld();
        try {
            Boolean keep = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
            return Boolean.TRUE.equals(keep);
        } catch (Throwable ignored) {
            // 极端情况取不到规则，按「继续处理」走，宁可多保护一次也不要漏判定
            return false;
        }
    }

    /** 保留背包、等级与经验。 */
    private void protect(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(config.isKeepLevel());
        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    private void warnIfNeeded(Player player) {
        int left = data.remaining(player);
        if (!config.getWarnAt().contains(left)) {
            return;
        }
        int baseLeft = Math.max(0, config.getBaseProtections() - data.getUsed(player));
        String template = config.message("warn");
        if (template == null || template.isEmpty()) {
            return;
        }
        player.sendMessage(Texts.format(template, left, baseLeft, data.getBonus(player)));
    }
}
