package com.tinyaii.tinygrace.command;

import com.tinyaii.tinygrace.TinyGrace;
import com.tinyaii.tinygrace.config.ConfigManager;
import com.tinyaii.tinygrace.data.GraceData;
import com.tinyaii.tinygrace.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /tinygrace 指令。
 *
 * <p>子命令同时支持英文与中文写法，方便服主在指南里用中文教玩家。
 */
public final class GraceCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "tinygrace.use";
    private static final String PERM_ADMIN = "tinygrace.admin";

    private final TinyGrace plugin;
    private final ConfigManager config;
    private final GraceData data;

    public GraceCommand(TinyGrace plugin, ConfigManager config, GraceData data) {
        this.plugin = plugin;
        this.config = config;
        this.data = data;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();

        if (sub.equals("reload") || sub.equals("重载")) {
            return reload(sender);
        }
        if (sub.equals("global") || sub.equals("全局")) {
            return global(sender, args);
        }
        if (sub.equals("help") || sub.equals("帮助") || sub.equals("?")) {
            help(sender, label);
            return true;
        }

        if (sub.equals("info") || sub.equals("信息") || sub.equals("status")) {
            return info(sender, args);
        }
        if (sub.equals("deaths") || sub.equals("死亡")) {
            return deaths(sender, args);
        }
        if (sub.equals("set") || sub.equals("设置")) {
            return setBonus(sender, args);
        }
        if (sub.equals("add") || sub.equals("增加")) {
            return addBonus(sender, args);
        }
        if (sub.equals("reset") || sub.equals("重置")) {
            return resetUsage(sender, args);
        }
        if (sub.equals("on") || sub.equals("开")) {
            return selfSwitch(sender, true);
        }
        if (sub.equals("off") || sub.equals("关")) {
            return selfSwitch(sender, false);
        }

        help(sender, label);
        return true;
    }

    // ────────────────────────── 帮助 ──────────────────────────

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "===== TinyGrace 有限死亡保护 =====");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info [玩家]" + ChatColor.WHITE
                + "  查看剩余保护次数");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " on / off" + ChatColor.WHITE
                + "  开关自己的死亡保护");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " deaths [玩家]" + ChatColor.WHITE
                + "  查看死亡次数");
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(ChatColor.GRAY + "--- 管理员 ---");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " set <玩家> <次数>" + ChatColor.WHITE
                    + "  设置额外保护次数");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " add <玩家> <次数>" + ChatColor.WHITE
                    + "  增加额外保护次数");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reset <玩家>" + ChatColor.WHITE
                    + "  清空该玩家的已用次数（重新发放配额）");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " global <on|off|toggle>" + ChatColor.WHITE
                    + "  切换全局保护");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.WHITE
                    + "  重载配置");
        }
        sender.sendMessage(ChatColor.GRAY + "基础 " + config.getBaseProtections() + " 次"
                + " | 全局 " + (config.isEnabled() ? "开启" : "关闭"));
    }

    // ────────────────────────── 查询 ──────────────────────────

    private boolean info(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, PERM_USE);
        if (target == null) {
            return true;
        }
        int total = data.total(target);
        int used = data.getUsed(target);
        int left = data.remaining(target);
        int bonus = data.getBonus(target);

        sender.sendMessage(ChatColor.GOLD + "===== " + target.getName() + " 的保护 =====");
        sender.sendMessage(Texts.color("&7总次数: &f" + total
                + " &8(基础 " + config.getBaseProtections() + " + 额外 " + bonus + ")"));
        sender.sendMessage(Texts.color("&7已消耗: &f" + used));
        sender.sendMessage(Texts.color("&7剩余: &e" + left));
        sender.sendMessage(Texts.color("&7死亡次数: &f" + data.getDeaths(target)));
        sender.sendMessage(Texts.color("&7个人开关: "
                + (data.isEnabled(target) ? "&a开启" : "&c关闭")));
        if (target.hasPermission("tinygrace.unlimited")) {
            sender.sendMessage(Texts.color("&d该玩家拥有无限保护权限（不消耗次数）"));
        }
        return true;
    }

    private boolean deaths(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, PERM_USE);
        if (target == null) {
            return true;
        }
        sender.sendMessage(Texts.color("&7" + target.getName() + " 累计死亡: &e"
                + data.getDeaths(target) + " &7次"));
        return true;
    }

    // ────────────────────────── 修改 ──────────────────────────

    private boolean setBonus(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Texts.color("&c用法: /tg set <玩家> <次数>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Texts.color("&c玩家不在线（该数据存在玩家身上，需在线才能改）"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 0) {
            sender.sendMessage(Texts.color("&c次数必须是不小于 0 的整数"));
            return true;
        }
        data.setBonus(target, value);
        sender.sendMessage(Texts.format("&a已将 &f{0} &a的额外保护设为 &e{1} &a次，当前剩余 &e{2} 次",
                target.getName(), value, data.remaining(target)));
        return true;
    }

    private boolean addBonus(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Texts.color("&c用法: /tg add <玩家> <次数>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Texts.color("&c玩家不在线（该数据存在玩家身上，需在线才能改）"));
            return true;
        }
        Integer delta = parseInt(args[2]);
        if (delta == null) {
            sender.sendMessage(Texts.color("&c次数必须是整数"));
            return true;
        }
        int newBonus = Math.max(0, data.getBonus(target) + delta);
        data.setBonus(target, newBonus);
        sender.sendMessage(Texts.format("&a已将 &f{0} &a的额外保护调整为 &e{1} &a次，当前剩余 &e{2} 次",
                target.getName(), newBonus, data.remaining(target)));
        return true;
    }

    private boolean resetUsage(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Texts.color("&c用法: /tg reset <玩家>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Texts.color("&c玩家不在线（该数据存在玩家身上，需在线才能改）"));
            return true;
        }
        data.resetUsage(target);
        sender.sendMessage(Texts.format("&a已清空 &f{0} &a的已用次数，当前剩余 &e{1} &a次",
                target.getName(), data.remaining(target)));
        return true;
    }

    // ────────────────────────── 开关 ──────────────────────────

    private boolean selfSwitch(CommandSender sender, boolean value) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "该子命令只能由玩家执行（管理员请用 global）。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(ChatColor.RED + "你没有权限。");
            return true;
        }
        data.setEnabled(player, value);
        String template = config.message(value ? "switch-on" : "switch-off");
        if (template == null || template.isEmpty()) {
            template = value
                    ? "&a✔ 已开启你的死亡保护（当前剩余 &f{0} &a次）"
                    : "&7已关闭你的死亡保护（死亡将正常掉落）";
        }
        player.sendMessage(Texts.format(template, data.remaining(player)));
        return true;
    }

    private boolean global(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Texts.color("&c用法: /tg global <on|off|toggle>"));
            return true;
        }
        String mode = args[1].toLowerCase();
        boolean value;
        if (mode.equals("on") || mode.equals("开") || mode.equals("true")) {
            value = true;
        } else if (mode.equals("off") || mode.equals("关") || mode.equals("false")) {
            value = false;
        } else if (mode.equals("toggle") || mode.equals("切换")) {
            value = !config.isEnabled();
        } else {
            sender.sendMessage(Texts.color("&c用法: /tg global <on|off|toggle>"));
            return true;
        }
        config.setEnabled(value);
        Bukkit.broadcastMessage(Texts.color("&6[TinyGrace] &f全局死亡保护已"
                + (value ? "&a开启" : "&c关闭")));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        config.reload();
        sender.sendMessage(ChatColor.GREEN + "✔ 配置已重载（基础 " + config.getBaseProtections()
                + " 次，全局 " + (config.isEnabled() ? "开启" : "关闭") + "）");
        return true;
    }

    // ────────────────────────── 工具 ──────────────────────────

    /** 解析目标玩家；无参默认自己。权限不足时直接提示并返回 null。 */
    private Player resolveTarget(CommandSender sender, String[] args, String perm) {
        if (args.length >= 2) {
            if (!sender.hasPermission(PERM_ADMIN)) {
                sender.sendMessage(ChatColor.RED + "只有管理员可以查看其他玩家。");
                return null;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Texts.color("&c玩家不在线。"));
                return null;
            }
            return target;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "控制台请指定玩家名。");
            return null;
        }
        Player self = (Player) sender;
        if (!self.hasPermission(perm)) {
            sender.sendMessage(ChatColor.RED + "你没有权限。");
            return null;
        }
        return self;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(ChatColor.RED + "你没有权限。");
            return false;
        }
        return true;
    }

    private Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(
                    Arrays.asList("info", "deaths", "on", "off", "help"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.addAll(Arrays.asList("set", "add", "reset", "global", "reload"));
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("global")) {
                return filter(Arrays.asList("on", "off", "toggle"), args[1]);
            }
            if (sub.equals("info") || sub.equals("deaths") || sub.equals("set")
                    || sub.equals("add") || sub.equals("reset")) {
                if (sender.hasPermission(PERM_ADMIN)) {
                    return filter(playerNames(), args[1]);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(p)) {
                out.add(c);
            }
        }
        return out;
    }
}
