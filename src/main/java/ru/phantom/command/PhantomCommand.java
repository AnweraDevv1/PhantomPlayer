package ru.phantom.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.phantom.PhantomPlugin;
import ru.phantom.core.BotManager;
import ru.phantom.core.PhantomBot;
import ru.phantom.util.Msg;

import java.util.ArrayList;
import java.util.List;

/** Основная команда /phantom. */
public class PhantomCommand implements CommandExecutor, TabCompleter {

    private final PhantomPlugin plugin;

    public PhantomCommand(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.getGui().openList(player);
            } else {
                help(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu" -> {
                if (requirePlayer(sender)) {
                    plugin.getGui().openList((Player) sender);
                }
            }
            case "create", "add" -> create(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "possess", "control" -> possess(sender, args);
            case "release", "exit" -> release(sender);
            case "gm", "gamemode" -> gamemode(sender, args);
            case "tp", "teleport" -> teleport(sender, args);
            case "list" -> list(sender);
            case "removeall" -> removeAll(sender);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void create(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "phantom.create")) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "<red>Использование: <white>/phantom create <ник>");
            return;
        }

        Player player = sender instanceof Player p ? p : null;
        // Из консоли бот спавнится в точке спавна основного мира.
        Location loc = player != null
                ? player.getLocation()
                : Bukkit.getWorlds().get(0).getSpawnLocation();

        BotManager.CreateResult result = plugin.getBotManager().create(args[1], loc, player);
        if (result.ok()) {
            Msg.send(sender, "<green>Бот <white>" + args[1] + "</white> создан.");
            if (player != null) {
                plugin.getGui().openMain(player, result.bot());
            }
        } else {
            Msg.send(sender, "<red>" + result.error());
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "phantom.create")) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "<red>Использование: <white>/phantom remove <ник>");
            return;
        }
        PhantomBot bot = plugin.getBotManager().byName(args[1]);
        if (bot == null) {
            Msg.send(sender, "<red>Бот не найден.");
            return;
        }
        if (!canManage(sender, bot)) {
            Msg.send(sender, "<red>Это не твой бот.");
            return;
        }
        plugin.getBotManager().remove(bot);
        Msg.send(sender, "<green>Бот удалён.");
    }

    private void possess(CommandSender sender, String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "phantom.possess")) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            Msg.send(sender, "<red>Использование: <white>/phantom possess <ник>");
            return;
        }
        PhantomBot bot = plugin.getBotManager().byName(args[1]);
        if (bot == null) {
            Msg.send(sender, "<red>Бот не найден.");
            return;
        }
        if (!canManage(sender, bot)) {
            Msg.send(sender, "<red>Это не твой бот.");
            return;
        }
        String error = plugin.getPossessionManager().possess(player, bot);
        if (error != null) {
            Msg.send(sender, "<red>" + error);
        }
    }

    private void release(CommandSender sender) {
        if (!requirePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!plugin.getPossessionManager().isPossessing(player)) {
            Msg.send(sender, "<red>Ты сейчас никем не управляешь.");
            return;
        }
        plugin.getPossessionManager().release(player);
    }

    /** Смена игрового режима — работает и во время вселения. */
    private void gamemode(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            Msg.send(sender, "<red>Использование: <white>/phantom gm <survival|creative|adventure|spectator>");
            return;
        }

        org.bukkit.GameMode mode = parseGameMode(args[1]);
        if (mode == null) {
            Msg.send(sender, "<red>Неизвестный режим: <white>" + args[1]);
            return;
        }

        PhantomBot bot = plugin.getPossessionManager().getPossessed(player);
        if (bot != null) {
            if (!plugin.getConfig().getBoolean("possession.allow-gamemode-change", true)) {
                Msg.send(sender, "<red>Смена режима во время вселения отключена в конфиге.");
                return;
            }
            // Режим меняем и себе, и боту — чтобы после выхода он сохранился.
            bot.getSettings().setGameMode(mode);
            player.setGameMode(mode);
            Player botPlayer = bot.getBukkitPlayer();
            if (botPlayer != null) {
                botPlayer.setGameMode(mode);
            }
            Msg.send(sender, "<green>Режим бота <white>" + bot.getName()
                    + "</white> изменён на <yellow>" + mode.name());
            return;
        }

        // Не вселён — меняем режим боту по имени.
        if (args.length >= 3) {
            PhantomBot target = plugin.getBotManager().byName(args[2]);
            if (target == null) {
                Msg.send(sender, "<red>Бот не найден.");
                return;
            }
            if (!canManage(sender, target)) {
                Msg.send(sender, "<red>Это не твой бот.");
                return;
            }
            target.getSettings().setGameMode(mode);
            target.applySettings();
            Msg.send(sender, "<green>Режим бота <white>" + target.getName()
                    + "</white> изменён на <yellow>" + mode.name());
        } else {
            Msg.send(sender, "<red>Ты не вселён в бота. Укажи имя: <white>/phantom gm <режим> <ник>");
        }
    }

    private org.bukkit.GameMode parseGameMode(String raw) {
        return switch (raw.toLowerCase()) {
            case "0", "s", "survival", "выживание" -> org.bukkit.GameMode.SURVIVAL;
            case "1", "c", "creative", "креатив" -> org.bukkit.GameMode.CREATIVE;
            case "2", "a", "adventure", "приключение" -> org.bukkit.GameMode.ADVENTURE;
            case "3", "sp", "spectator", "наблюдатель" -> org.bukkit.GameMode.SPECTATOR;
            default -> null;
        };
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "<red>Использование: <white>/phantom tp <ник>");
            return;
        }
        PhantomBot bot = plugin.getBotManager().byName(args[1]);
        if (bot == null || bot.getLocation() == null) {
            Msg.send(sender, "<red>Бот не найден.");
            return;
        }
        ((Player) sender).teleport(bot.getLocation());
        Msg.send(sender, "<green>Телепорт к <white>" + bot.getName());
    }

    private void list(CommandSender sender) {
        var bots = plugin.getBotManager().all();
        if (bots.isEmpty()) {
            Msg.send(sender, "<gray>Ботов пока нет.");
            return;
        }
        Msg.send(sender, "<aqua>Всего ботов: <white>" + bots.size());
        for (PhantomBot bot : bots) {
            Player bukkit = bot.getBukkitPlayer();
            String owner = bot.getOwnerUuid() == null
                    ? "консоль"
                    : String.valueOf(Bukkit.getOfflinePlayer(bot.getOwnerUuid()).getName());
            Msg.raw(sender, "<dark_gray>• <white>" + bot.getName()
                    + " <gray>| HP <red>" + (bukkit == null ? "?" : String.format("%.0f", bukkit.getHealth()))
                    + " <gray>| ИИ <yellow>" + bot.getSettings().getAiMode().display()
                    + " <gray>| владелец <white>" + owner);
        }
    }

    private void removeAll(CommandSender sender) {
        if (!requirePermission(sender, "phantom.admin")) {
            return;
        }
        int count = plugin.getBotManager().size();
        plugin.getBotManager().removeAll();
        Msg.send(sender, "<green>Удалено ботов: <white>" + count);
    }

    private void reload(CommandSender sender) {
        if (!requirePermission(sender, "phantom.admin")) {
            return;
        }
        plugin.reloadConfig();
        Msg.setPrefix(plugin.getConfig().getString("messages.prefix"));
        Msg.send(sender, "<green>Конфиг перезагружен.");
    }

    private void help(CommandSender sender) {
        Msg.raw(sender, "<dark_gray><st>                    </st> <gradient:#8A2BE2:#00E5FF><bold>PhantomPlayer</bold></gradient> <dark_gray><st>                    </st>");
        Msg.raw(sender, "<aqua>/phantom <gray>— открыть меню ботов");
        Msg.raw(sender, "<aqua>/phantom create <ник> <gray>— создать бота");
        Msg.raw(sender, "<aqua>/phantom remove <ник> <gray>— удалить бота");
        Msg.raw(sender, "<aqua>/phantom possess <ник> <gray>— вселиться в бота");
        Msg.raw(sender, "<aqua>/phantom release <gray>— выйти из бота");
        Msg.raw(sender, "<aqua>/phantom gm <режим> <gray>— сменить режим (креатив и т.д.)");
        Msg.raw(sender, "<aqua>/phantom tp <ник> <gray>— телепорт к боту");
        Msg.raw(sender, "<aqua>/phantom list <gray>— список ботов");
        Msg.raw(sender, "<aqua>/phantom removeall <gray>— удалить всех <dark_gray>(админ)");
        Msg.raw(sender, "<aqua>/phantom reload <gray>— перезагрузить конфиг <dark_gray>(админ)");
    }

    // ------------------------------------------------------------------

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, "<red>Эта команда только для игроков.");
            return false;
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            Msg.send(sender, "<red>Недостаточно прав.");
            return false;
        }
        return true;
    }

    private boolean canManage(CommandSender sender, PhantomBot bot) {
        if (sender.hasPermission("phantom.admin") || !(sender instanceof Player player)) {
            return true;
        }
        return player.getUniqueId().equals(bot.getOwnerUuid());
    }

    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("menu", "create", "remove", "possess", "release",
                    "gm", "tp", "list", "removeall", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("gm")) {
            for (String mode : List.of("survival", "creative", "adventure", "spectator")) {
                if (mode.startsWith(args[1].toLowerCase())) {
                    out.add(mode);
                }
            }
        } else if (args.length == 2 && List.of("remove", "possess", "tp").contains(args[0].toLowerCase())) {
            for (PhantomBot bot : plugin.getBotManager().all()) {
                if (bot.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(bot.getName());
                }
            }
        }
        return out;
    }
}
