package ru.phantom.control;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.phantom.PhantomPlugin;
import ru.phantom.core.PhantomBot;
import ru.phantom.util.Msg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Режим «вселения»: игрок буквально играет за бота.
 * <p>
 * <b>Как это работает.</b> Прошлая версия использовала режим наблюдателя и камеру
 * ({@code setSpectatorTarget}) — из-за этого нельзя было нормально ходить, бить и
 * взаимодействовать с миром. Теперь схема другая: игрок телепортируется в тело бота,
 * его самого прячут от всех остальных ({@code hidePlayer}), а бот каждый тик
 * зеркалит позицию, поворот и позу игрока.
 * <p>
 * Результат: движение, прыжки, физика и удары считает клиент самого игрока —
 * то есть всё работает абсолютно нативно, как будто он зашёл с другого ника.
 * Со стороны остальных виден только бот.
 */
public class PossessionManager {

    private final PhantomPlugin plugin;

    private final Map<UUID, PhantomBot> possessions = new HashMap<>();
    private final Map<UUID, SavedState> savedStates = new HashMap<>();

    public PossessionManager(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    /** Состояние реального игрока до вселения. */
    private record SavedState(Location location, GameMode gameMode, boolean invulnerable,
                              boolean allowFlight, boolean flying,
                              ItemStack[] inventory, ItemStack[] armor,
                              double health, int foodLevel, float exp, int level) {
    }

    /**
     * Вселяет игрока в бота.
     *
     * @return текст ошибки или null при успехе
     */
    public String possess(Player player, PhantomBot bot) {
        if (!bot.isSpawned()) {
            return "Бот не заспавнен";
        }
        if (isPossessing(player)) {
            return "Ты уже управляешь ботом. Сначала выйди: /phantom release";
        }
        if (bot.isPossessed()) {
            return "Этим ботом уже управляет другой игрок";
        }

        Location botLoc = bot.getLocation();
        if (botLoc == null) {
            return "Не удалось определить позицию бота";
        }

        int maxDistance = plugin.getConfig().getInt("possession.max-distance", -1);
        if (maxDistance > 0) {
            if (!botLoc.getWorld().equals(player.getWorld())
                    || botLoc.distance(player.getLocation()) > maxDistance) {
                return "Бот слишком далеко (максимум " + maxDistance + " блоков)";
            }
        }

        // Сохраняем всё, что будем подменять.
        savedStates.put(player.getUniqueId(), new SavedState(
                player.getLocation().clone(),
                player.getGameMode(),
                player.isInvulnerable(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getExp(),
                player.getLevel()));

        possessions.put(player.getUniqueId(), bot);
        bot.setPossessedBy(player.getUniqueId());
        bot.getSettings().setAiMode(ru.phantom.config.BotSettings.AiMode.IDLE);

        Player botPlayer = bot.getBukkitPlayer();

        // Игрок занимает место бота и получает его игровой режим.
        player.teleport(botLoc);
        player.setGameMode(bot.getSettings().getGameMode());

        // Инвентарь бота становится инвентарём игрока — можно строить, есть, бить.
        if (plugin.getConfig().getBoolean("possession.sync-inventory-view", true) && botPlayer != null) {
            player.getInventory().setContents(botPlayer.getInventory().getContents());
            player.getInventory().setArmorContents(botPlayer.getInventory().getArmorContents());
            player.getInventory().setHeldItemSlot(botPlayer.getInventory().getHeldItemSlot());
        }

        // Прячем настоящее тело игрока: окружающие должны видеть только бота.
        if (plugin.getConfig().getBoolean("possession.hide-real-body", true)) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    viewer.hidePlayer(plugin, player);
                }
            }
        }

        // Бот не должен мешать своему же водителю.
        if (botPlayer != null) {
            botPlayer.setCollidable(false);
            player.hidePlayer(plugin, botPlayer);
        }

        Msg.send(player, "<green>Ты вселился в <white>" + bot.getName() + "</white>.");
        Msg.raw(player, "<gray>Играй как обычно: <white>ходи, бей, стройся, пиши в чат</white>. "
                + "Остальные видят бота.");
        Msg.raw(player, "<gray>Выход: <white>/phantom release</white> или <white>Shift+Q</white>.");
        return null;
    }

    /** Выход из бота с полным восстановлением состояния. */
    public void release(Player player) {
        PhantomBot bot = possessions.remove(player.getUniqueId());
        SavedState state = savedStates.remove(player.getUniqueId());

        if (bot != null) {
            bot.setPossessedBy(null);
            Player botPlayer = bot.getBukkitPlayer();

            // Инвентарь, накопленный за время игры, остаётся боту.
            if (botPlayer != null) {
                botPlayer.getInventory().setContents(player.getInventory().getContents());
                botPlayer.getInventory().setArmorContents(player.getInventory().getArmorContents());
                botPlayer.setCollidable(bot.getSettings().isCollidable());

                // Бот остаётся стоять там, где игрок закончил.
                bot.snapTo(player.getLocation());
                player.showPlayer(plugin, botPlayer);
            }
        }

        // Возвращаем игрока в его тело.
        if (state != null) {
            player.getInventory().setContents(state.inventory());
            player.getInventory().setArmorContents(state.armor());
            player.setGameMode(state.gameMode());
            player.setInvulnerable(state.invulnerable());
            player.setAllowFlight(state.allowFlight());
            player.setFlying(state.flying());
            player.setFoodLevel(state.foodLevel());
            player.setExp(state.exp());
            player.setLevel(state.level());
            try {
                player.setHealth(Math.min(state.health(), player.getMaxHealth()));
            } catch (IllegalArgumentException ignored) {
            }
            player.teleport(state.location());
        }

        // Показываем игрока остальным.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.showPlayer(plugin, player);
            }
        }

        Msg.send(player, "<yellow>Ты вернулся в своё тело.");
    }

    /** Освобождает бота, если им кто-то управляет. */
    public void releaseIfPossessed(PhantomBot bot) {
        UUID possessor = bot.getPossessedBy();
        if (possessor == null) {
            return;
        }
        Player player = Bukkit.getPlayer(possessor);
        if (player != null) {
            release(player);
        } else {
            possessions.remove(possessor);
            savedStates.remove(possessor);
            bot.setPossessedBy(null);
        }
    }

    public void releaseAll() {
        for (UUID uuid : new HashMap<>(possessions).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                release(player);
            }
        }
        possessions.clear();
        savedStates.clear();
    }

    public boolean isPossessing(Player player) {
        return possessions.containsKey(player.getUniqueId());
    }

    public PhantomBot getPossessed(Player player) {
        return possessions.get(player.getUniqueId());
    }

    public Map<UUID, PhantomBot> getPossessions() {
        return possessions;
    }
}
