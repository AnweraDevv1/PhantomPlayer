package ru.phantom.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.phantom.PhantomPlugin;
import ru.phantom.config.BotSettings;

/** Правила, отличающие бота от обычного игрока (реализм, смерть, респавн). */
public class BotListener implements Listener {

    private final PhantomPlugin plugin;

    public BotListener(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    /** Фильтрация урона по настройкам реализма. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PhantomBot bot = plugin.getBotManager().fromPlayer(player);
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();

        if (settings.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if (!settings.isTakeFireDamage()) {
                    event.setCancelled(true);
                }
            }
            case DROWNING -> {
                if (!settings.isDrowning()) {
                    event.setCancelled(true);
                }
            }
            case STARVATION -> {
                if (!settings.isHunger()) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /** Голод у ботов по умолчанию отключён. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PhantomBot bot = plugin.getBotManager().fromPlayer(player);
        if (bot != null && !bot.getSettings().isHunger()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    /** Смерть бота: дроп, оповещение владельца, авто-респавн. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PhantomBot bot = plugin.getBotManager().fromPlayer(player);
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();

        if (!settings.isDropInventoryOnDeath()) {
            event.getDrops().clear();
            event.setKeepInventory(true);
        }

        // Освобождаем игрока, если он был «внутри» бота.
        if (bot.isPossessed() && plugin.getConfig().getBoolean("possession.release-on-death", true)) {
            Player possessor = Bukkit.getPlayer(bot.getPossessedBy());
            if (possessor != null) {
                plugin.getPossessionManager().release(possessor);
                ru.phantom.util.Msg.send(possessor, "<red>Тело бота погибло — тебя выкинуло обратно.");
            }
        }

        if (bot.getOwnerUuid() != null) {
            Player owner = Bukkit.getPlayer(bot.getOwnerUuid());
            if (owner != null) {
                ru.phantom.util.Msg.send(owner, "<red>Твой бот <white>" + bot.getName() + "</white> погиб.");
            }
        }

        if (settings.isAutoRespawn()) {
            bot.setDeathTicks(settings.getAutoRespawnDelay());
        }
    }

    /** При техническом переспавне (смена скина) не шумим в чат. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotJoinMessage(PlayerJoinEvent event) {
        PhantomBot bot = plugin.getBotManager().fromPlayer(event.getPlayer());
        if (bot != null && bot.isSilentRespawn()) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotQuitMessage(org.bukkit.event.player.PlayerQuitEvent event) {
        PhantomBot bot = plugin.getBotManager().fromPlayer(event.getPlayer());
        if (bot != null && bot.isSilentRespawn()) {
            event.quitMessage(null);
        }
    }

    /** Новому игроку показываем ботов в TAB согласно их настройкам. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            for (PhantomBot bot : plugin.getBotManager().all()) {
                if (bot.isSpawned()) {
                    bot.updateTabVisibility();
                }
            }
        }, 20L);
    }
}
