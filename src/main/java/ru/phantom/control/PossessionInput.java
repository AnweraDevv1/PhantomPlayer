package ru.phantom.control;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import ru.phantom.PhantomPlugin;
import ru.phantom.core.PhantomBot;

/**
 * Синхронизация действий вселившегося игрока с телом бота.
 * <p>
 * Позиция и поворот зеркалятся каждый тик в {@link PhantomPlugin}, а здесь
 * ловятся дискретные действия: взмахи, приседание, бег, смена предмета, чат.
 */
public class PossessionInput implements Listener {

    private final PhantomPlugin plugin;

    public PossessionInput(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    /** Взмах рукой игрока — бот машет так же. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        PhantomBot bot = plugin.getPossessionManager().getPossessed(event.getPlayer());
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            bot.swingHand();
        }
    }

    /** Приседание. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        PhantomBot bot = plugin.getPossessionManager().getPossessed(event.getPlayer());
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        Player botPlayer = bot.getBukkitPlayer();
        if (botPlayer != null) {
            botPlayer.setSneaking(event.isSneaking());
            bot.getSettings().setSneaking(event.isSneaking());
            bot.refreshMetadata();
        }
    }

    /** Бег. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        PhantomBot bot = plugin.getPossessionManager().getPossessed(event.getPlayer());
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        Player botPlayer = bot.getBukkitPlayer();
        if (botPlayer != null) {
            botPlayer.setSprinting(event.isSprinting());
            bot.refreshMetadata();
        }
    }

    /** Смена предмета в руке — чтобы окружающие видели то же оружие. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        PhantomBot bot = plugin.getPossessionManager().getPossessed(event.getPlayer());
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        Player botPlayer = bot.getBukkitPlayer();
        if (botPlayer != null) {
            botPlayer.getInventory().setHeldItemSlot(event.getNewSlot());
            bot.syncEquipment(event.getPlayer(), event.getNewSlot());
        }
    }

    /** Выброшенные вещи убираем из инвентаря бота тоже. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        PhantomBot bot = plugin.getPossessionManager().getPossessed(event.getPlayer());
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            Player botPlayer = bot.getBukkitPlayer();
            if (botPlayer != null) {
                botPlayer.getInventory().setContents(event.getPlayer().getInventory().getContents());
                bot.syncEquipment(event.getPlayer(), event.getPlayer().getInventory().getHeldItemSlot());
            }
        });
    }

    /**
     * Сообщения в чат отправляются от имени бота.
     * <p>
     * Отменяем оригинальное событие и заставляем говорить бота, иначе
     * в чате светился бы настоящий ник игрока.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PhantomBot bot = plugin.getPossessionManager().getPossessed(player);
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        // Если плагин ждёт ввода значения — не перехватываем.
        if (plugin.getChatInput().isWaiting(player)) {
            return;
        }
        if (!bot.getSettings().isChatEnabled()) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            Player botPlayer = bot.getBukkitPlayer();
            if (botPlayer != null) {
                botPlayer.chat(message);
            }
        });
    }

    /**
     * Урон, который наносит вселившийся игрок, засчитывается боту.
     * <p>
     * Реального игрока не видно, поэтому логичнее, чтобы источником урона
     * в логах и статистике выглядел бот.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        PhantomBot bot = plugin.getPossessionManager().getPossessed(player);
        if (bot == null || !bot.isSpawned()) {
            return;
        }
        // Бот не должен бить сам себя.
        if (event.getEntity().equals(bot.getBukkitPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Выход игрока — освобождаем бота. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPossessionManager().isPossessing(player)) {
            plugin.getPossessionManager().release(player);
        }
    }
}
