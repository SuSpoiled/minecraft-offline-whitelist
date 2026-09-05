package com.example.whitelist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class WhitelistPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final String ADMIN = "whitelist.admin";
    private static final String[] SUBCOMMANDS = {"add", "remove", "list", "reload", "toggle"};

    private final Set<String> list = ConcurrentHashMap.newKeySet();

    private volatile boolean enabled;
    private volatile boolean bypassForOp;
    private volatile boolean logEvents;
    private volatile Component kickMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refresh();

        CommandHandler handler = new CommandHandler();
        PluginCommand cmd = getCommand("whitelist");
        if (cmd != null) {
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PaperWhitelist 已启用，白名单共 " + list.size() + " 人。");
    }

    @Override
    public void onDisable() {
        saveList();
    }

    private void refresh() {
        loadList();
        readConfig();
    }

    private boolean isAllowed(String name) {
        if (list.contains(name)) {
            return true;
        }
        if (!bypassForOp) {
            return false;
        }
        try {
            for (OfflinePlayer op : getServer().getOperators()) {
                if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) {
            return;
        }
        String name = event.getName().toLowerCase(Locale.ROOT);
        if (isAllowed(name)) {
            return;
        }
        if (logEvents) {
            getLogger().info("拒绝 " + name + "，不在白名单内。");
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage);
    }

    private void loadList() {
        list.clear();
        for (String raw : getConfig().getStringList("whitelist")) {
            String name = raw.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty() || !NAME.matcher(name).matches()) {
                getLogger().warning("config.yml 里有不合法的玩家名，已忽略: " + raw);
                continue;
            }
            list.add(name);
        }
    }

    private void saveList() {
        getConfig().set("whitelist", new ArrayList<>(list));
        try {
            saveConfig();
        } catch (Exception ex) {
            getLogger().severe("config.yml 保存失败: " + ex.getMessage());
        }
    }

    private void readConfig() {
        enabled = getConfig().getBoolean("enabled", true);
        bypassForOp = getConfig().getBoolean("bypass-for-op", true);
        logEvents = getConfig().getBoolean("log-events", true);
        kickMessage = AMP.deserialize(
                getConfig().getString("kick-message", "&c不在白名单内"));
    }

    private Component msg(String key, String... replacements) {
        String raw = getConfig().getString("messages." + key, "&c提示语不存在。");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return AMP.deserialize(raw);
    }

    private final class CommandHandler implements CommandExecutor, TabCompleter {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission(ADMIN)) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(msg("usage", "%command%", "/" + label));
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("remove")) {
                if (args.length < 2) {
                    sender.sendMessage(msg("usage", "%command%", "/" + label));
                    return true;
                }
                String name = args[1].trim().toLowerCase(Locale.ROOT);
                if (!NAME.matcher(name).matches()) {
                    sender.sendMessage(msg("not-found", "%player%", args[1]));
                    return true;
                }
                boolean added = sub.equals("add");
                boolean changed = added ? list.add(name) : list.remove(name);
                if (changed) {
                    saveList();
                }
                sender.sendMessage(msg(added ? (changed ? "added" : "already-added") : (changed ? "removed" : "not-removed"),
                        "%player%", name, "%total%", String.valueOf(list.size())));
                return true;
            }
            switch (sub) {
                case "list": {
                    listPages(sender, args);
                    return true;
                }
                case "reload": {
                    refresh();
                    sender.sendMessage(msg("reloaded"));
                    return true;
                }
                case "toggle": {
                    enabled = !enabled;
                    getConfig().set("enabled", enabled);
                    saveConfig();
                    getServer().broadcast(msg(enabled ? "now-enabled" : "now-disabled"));
                    return true;
                }
                default: {
                    sender.sendMessage(msg("usage", "%command%", "/" + label));
                    return true;
                }
            }
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (!sender.hasPermission(ADMIN) || args.length == 0) {
                return List.of();
            }
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (args.length == 1) {
                List<String> options = new ArrayList<>(SUBCOMMANDS.length);
                for (String s : SUBCOMMANDS) {
                    if (s.startsWith(prefix)) {
                        options.add(s);
                    }
                }
                return options;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("remove")) {
                List<String> names = new ArrayList<>();
                for (Player player : getServer().getOnlinePlayers()) {
                    String name = player.getName().toLowerCase(Locale.ROOT);
                    if (name.startsWith(prefix)) {
                        names.add(name);
                    }
                }
                names.sort(String.CASE_INSENSITIVE_ORDER);
                return names;
            }
            return List.of();
        }

        private void listPages(CommandSender sender, String[] args) {
            List<String> all = new ArrayList<>(list);
            all.sort(String.CASE_INSENSITIVE_ORDER);
            if (all.isEmpty()) {
                sender.sendMessage(msg("list-empty"));
                return;
            }
            int pageSize = Math.max(1, getConfig().getInt("list-page-size", 10));
            int pages = (all.size() + pageSize - 1) / pageSize;
            int page = 1;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            page = Math.min(Math.max(1, page), pages);
            sender.sendMessage(msg("list-header",
                    "%page%", String.valueOf(page),
                    "%pages%", String.valueOf(pages),
                    "%total%", String.valueOf(all.size())));
            int end = Math.min(page * pageSize, all.size());
            for (int i = (page - 1) * pageSize; i < end; i++) {
                sender.sendMessage(AMP.deserialize("&8- &f" + all.get(i)));
            }
        }
    }
}
